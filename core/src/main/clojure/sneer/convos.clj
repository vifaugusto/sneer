(ns sneer.convos
  (:require
    [clojure.core.async :as async :refer [go chan close! <! >! sliding-buffer alt! timeout]]
    [clojure.stacktrace :refer [print-stack-trace]]
    [rx.lang.clojure.core :as rx]
    [sneer.async :refer [sliding-chan go-while-let go-loop-trace link-chan-to-subscriber thread-chan-to-subscriber]]
    [sneer.commons :refer [now produce! descending]]
    [sneer.contact :refer [get-contacts puk->contact]]
    [sneer.conversation :refer :all]
    [sneer.io :as io]
    [sneer.rx :refer [shared-latest]]
    [sneer.party :refer [party->puk]]
    [sneer.serialization :refer [serialize deserialize]]
    [sneer.tuple.protocols :refer :all]
    [sneer.tuple-base-provider :refer :all]
    [sneer.tuple.persistent-tuple-base :as tb])
  (:import
    [java.io File]
    [java.util Date]
    [org.ocpsoft.prettytime PrettyTime]
    [rx Subscriber]
    [sneer.admin SneerAdmin]
    [sneer.async LeaseHolder]
    [sneer.commons Clock PersistenceFolder]
    [sneer.convos Convos Convos$Summary]))

(defn- contact-puk [tuple]
  (tuple "party"))

(defn- handle-contact [own-puk tuple state]
  (let [contact-puk (contact-puk tuple)]
    (cond-> state
      (and (some? contact-puk) (= (tuple "author") own-puk))
      (update-in [contact-puk]
                 assoc :name      (tuple "payload")
                       :timestamp (tuple "timestamp")))))

(defn- unread-status [label old-status]
  (cond
    (= old-status "?") "?"
    (.contains label "?") "?"
    :else "*"))

(defn- update-with-received [id label old-summary]
  (-> old-summary
    (assoc :last-received id)
    (update :unread #(unread-status label %))))

(defn- update-summary [own? message old-summary]
  (let [label     (message "label")
        timestamp (message "timestamp")]
    (->
      (if own?
        old-summary
        (update-with-received (message "original_id") label old-summary))
      (assoc
        :preview (or label "")
        :timestamp timestamp))))

(defn- handle-message [own-puk message state]
  (let [author (message "author")
        own? (= author own-puk)
        contact-puk (if own? (message "audience") author)]
    (update-in state
               [contact-puk]
               #(update-summary own? message %))))

(defn- update-with-read [summary tuple]
  (let [msg-id (tuple "payload")]
    (cond-> summary
      (= msg-id (:last-received summary)) (assoc :unread ""))))

(defn- handle-read-receipt [own-puk tuple state]
  (let [contact-puk (tuple "audience")]
    (cond-> state
      (= (tuple "author") own-puk)
      (update-in [contact-puk]
                 update-with-read tuple))))

(defn summarize [state]
  (let [pretty-time (PrettyTime. (Date. (Clock/now)))
        with-pretty-date (fn [summary]
                           (assoc summary :date
                                          (.format pretty-time (Date. ^long (summary :timestamp)))))]
    (->> state
         vals
         (filter :name)
         (sort-by :timestamp descending)
         (mapv with-pretty-date))))

(defn link-lease-to-chan
  "Closes `linked' when `lease' emits a value."
  [lease linked]
  (go
    (<! lease)
    (close! linked)))

(defn start-summarization-machine! [state own-puk tuple-base summaries-out lease]
  (let [last-id (:last-id state)
        tuples (chan)
        all-tuples-criteria {tb/after-id last-id}]
    (query-tuples tuple-base all-tuples-criteria tuples lease)

    ; link machine lifetime to lease
    (link-lease-to-chan lease tuples)

    (go-loop-trace [state state]
      (>! summaries-out state)

      (if-let [tuple (<! tuples)]
        (do
          (recur
            (->
              (case (tuple "type")
                "contact" (handle-contact own-puk tuple state)
                "message" (handle-message own-puk tuple state)
                "message-read" (handle-read-receipt own-puk tuple state)
                state)
              (assoc :last-id (tuple "id")))))))))


; Java interface

(defn- to-foreign-summary [{:keys [name summary date unread]}]
  (println "TODO: CONVERSATION ID")
  (Convos$Summary. name summary date (str unread) -4242))

(defn- to-foreign [summaries]
  (->> summaries summarize (mapv to-foreign-summary)))

(defn- republish-latest-every [period in out]
  (go-loop-trace [latest nil
                  period-timeout (timeout period)]
    (alt! :priority true
          in
          ([latest]
           (when latest
             (>! out latest)
             (recur latest period-timeout)))

          period-timeout
          ([_]
           (when latest
             (>! out latest))
           (recur latest (timeout period))))))

(defn- read-snapshot [file]
  (let [default {}]
    (if (and file (.exists file))
      (try
        (deserialize (io/read-bytes file))
        (catch Exception e
          (println "Exception reading snapshot:" (.getMessage e))
          default))
      default)))

(defn- write-snapshot [file snapshot]
  (when file
    (io/write-bytes file (serialize snapshot))
    (println "Snapshot written:" (-> snapshot count dec)    ; dec: Don't count the :last-id key.
             "conversations, last-id:" (:last-id snapshot))))

(defn- start-saving-snapshots-to! [file ch]
  (let [never (chan)]
    (go-loop-trace [snapshot nil
                    debounce never]
      (alt! :priority true
        ch       ([snapshot]
                   (when snapshot
                     (recur snapshot (timeout 5000))))
        debounce ([_]
                   (write-snapshot file snapshot)
                   (recur nil never))))))

(defn- sliding-tap [mult]
  (let [ch (sliding-chan)]
    (async/tap mult ch)
    ch))

(defn- -summaries [container]
  (rx/observable*
    (fn [^Subscriber subscriber]
      (let [lease (.getLeaseChannel (.produce container LeaseHolder))
            file (some-> (.produce container PersistenceFolder)
                         (.get)
                         (File. "conversation-summaries.tmp"))
            admin (.produce container SneerAdmin)
            own-puk (.. admin privateKey publicKey)
            tuple-base (tuple-base-of admin)
            summaries-out (sliding-chan)
            summaries-out-mult (async/mult summaries-out)
            tap-summaries #(sliding-tap summaries-out-mult)
            pretty-summaries-out (chan (sliding-buffer 1) (map to-foreign))]
        (link-chan-to-subscriber summaries-out subscriber)
        (link-lease-to-chan lease pretty-summaries-out)
        (republish-latest-every (* 60 1000) (tap-summaries) pretty-summaries-out)
        (start-saving-snapshots-to! file (tap-summaries))
        (start-summarization-machine! (read-snapshot file) own-puk tuple-base summaries-out lease)
        (thread-chan-to-subscriber pretty-summaries-out subscriber "conversation summaries")))))

(defn reify-Convos [container]
  (let [shared-summaries (atom nil)]
    (reify Convos
      (summaries [_]
        (swap! shared-summaries #(if % % (shared-latest (do-summaries container)))))
      (problemWithNewNickname [_ newContactNick])
      (startConvo [_ newContactNick] -345354))))