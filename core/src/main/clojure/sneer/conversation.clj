(ns sneer.conversation
  (:require
   [rx.lang.clojure.core :as rx]
   [rx.lang.clojure.interop :as interop]
   [sneer.rx :refer [atom->observable subscribe-on-io latest shared-latest combine-latest]]
   [sneer.party :refer [party-puk]]
   [sneer.commons :refer [now produce!]]
   [sneer.tuple.space :refer [payload]])
  (:import
   [sneer PublicKey Party Contact Conversations Conversation Message Conversations$Notification]
   [sneer.tuples Tuple TupleSpace]
   [java.text SimpleDateFormat]
   [rx.subjects BehaviorSubject]
   [rx Observable]))

(def simple-date-format (SimpleDateFormat. "HH:mm"))

(defn format-date [time] (.format ^SimpleDateFormat simple-date-format time))

(defn reify-message [own-puk ^Tuple tuple]
  (let [created (.timestamp tuple)
        type (.type tuple)
        jpeg-image ^bytes (.get tuple "jpeg-image")
        label (.get tuple "label")
        label (if label label (if jpeg-image "" type))
        own? (= own-puk (.author tuple))]

    (reify Message
      (isOwn [_] own?)
      (label [_] label)
      (jpegImage [_] jpeg-image)
      (timestampCreated [_] created)
      (timestampReceived [_] 0)
      (timeCreated [_] (format-date created))
      (tuple [_] tuple)
      Object
      (toString [_] label))))

(defn original-id [^Message message]
  (get (.tuple message) "original_id"))

(defn own? [^Message message]
  (.isOwn message))

(defn- message-label [^Message message]
  (.label message))

(defn- message-timestamp [^Message message]
  (.timestampCreated message))

(defn- reverse-party-messages [messages]
  (->> messages reverse (remove own?)))

(defn- most-recent-message [^Observable observable-messages]
  (rx/flatmap
   (fn [messages]
     (if-some [message (-> messages reverse first)]
       (rx/return message)
       (rx/empty)))
   observable-messages))

(defn- unread-messages [messages last-read-id]
  (->> (reverse-party-messages messages)
       (take-while #(> (original-id %) last-read-id))
       vec))

(defn- latest-unread-messages
  [^Observable observable-messages ^Observable last-read-filter]
  (let [last-read-ids (rx/map payload last-read-filter)]
    (latest
     (Observable/combineLatest observable-messages
                               (rx/cons 0 last-read-ids)
                               (interop/fn [messages last-read-id]
                                 (unread-messages messages last-read-id))))))

(defn values-to-compare [^Message msg] [(-> msg .tuple (get "id"))])

(def message-comparator (fn [m1 m2] (compare (values-to-compare m1) (values-to-compare m2))))

(defn reify-conversation
  [^TupleSpace tuple-space ^Observable conversation-menu-items ^PublicKey own-puk ^Party party]

  (let [^PublicKey party-puk (party-puk party)
        messages (atom (sorted-set-by message-comparator))
        observable-messages (rx/map vec (atom->observable messages))
        message-filter (.. tuple-space filter (type "message"))
        msg-tuples-out (.. message-filter (author own-puk  ) (audience party-puk) tuples)
        msg-tuples-in  (.. message-filter (author party-puk) (audience own-puk  ) tuples)
        last-read-pub (.. tuple-space
                          publisher
                          (type "message-read")
                          (audience party-puk))
        last-read-filter (.. tuple-space
                             filter
                             last
                             (type "message-read")
                             (audience party-puk)
                             (author own-puk)
                             tuples)
        unread-messages (latest-unread-messages observable-messages last-read-filter)
        most-recent-message (most-recent-message observable-messages)]

    (subscribe-on-io
      (rx/merge msg-tuples-out
                msg-tuples-in)
      (fn [tuple]
        (swap! messages conj (reify-message own-puk tuple))))

    (reify
      Conversation
      (party [_] party)

      (messages [_]
        observable-messages)

      (unreadMessages [_]
        unread-messages)

      (sendMessage [_ label]
        (..
          tuple-space
          publisher
          (audience party-puk)
          (field "message-type" "chat")
          (type "message")
          (field "label" label)
          (pub)))

      (mostRecentMessageContent [_]
        (rx/map message-label
                most-recent-message))

      (mostRecentMessageTimestamp [_]
        (rx/map message-timestamp
                most-recent-message))

      (menu [_]
        conversation-menu-items)

      (unreadMessageCount [_]
        (->> unread-messages
             (rx/map (comp long count))))

      (setRead [_ message]
        (assert (-> message own? not))
        (.pub last-read-pub (original-id message))))))

(defn- reify-notification [conversations title text subText]
  (reify Conversations$Notification
    (conversations [_] conversations)
    (title [_] title)
    (text [_] text)
    (subText [_] subText)))

(defn- unread-messages-label [count]
  (str count " unread message" (when-not (= 1 count) "s")))

(defn- notification-for-single [[^Conversation c unread-messages]]
  (rx/map
   (fn [party-name]
     (let [text (message-label (first unread-messages))
           subText (unread-messages-label (count unread-messages))]
       (reify-notification [c] party-name text subText)))
   (.. c party name first)))

(defn- notification-for-many [unread-conversations]
  (let [conversations (mapv first unread-conversations)
        text ""
        unread-count (->> unread-conversations (map second) (map count) (reduce +))
        subText (unread-messages-label unread-count)]
    (rx/return
     (reify-notification conversations "New messages" text subText))))

(defn reify-conversations [own-puk tuple-space contacts]
  (let [menu-items (BehaviorSubject/create [])
        convos (atom {})
        reify-conversation (partial reify-conversation tuple-space (.asObservable menu-items) own-puk)
        produce-conversation (partial produce! reify-conversation convos)]

    (reify Conversations

      (all [_]
        (->> contacts
             (rx/map (partial map (fn [^Contact c] (produce-conversation (.party c)))))
             shared-latest))

      (ofType [_ type]
        (rx/never))

      (with [_ party]
        (produce-conversation party))

      (notifications [this]
        (->> (.all this)

             ;; [Conversation]
             (rx/map (fn [conversations]
                       (->> conversations
                            (mapv (fn [^Conversation c]
                                    (->> (.unreadMessages c)
                                         (rx/map (partial vector c))))))))

             ;; [Observable (Conversation, [Message])]
             (rx/flatmap
              (partial combine-latest
                       (partial filterv (comp not empty? second))))

             ;; [(Conversation, [Message])]
             (rx/flatmap
              (fn [unread-pairs]
                (case (count unread-pairs)
                  0 (rx/empty)
                  1 (notification-for-single (first unread-pairs))
                  (notification-for-many unread-pairs))))))

      (setMenuItems [_ menu-item-list]
        (rx/on-next menu-items menu-item-list)))))
