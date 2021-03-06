(defproject me.sneer/core "0.1.5"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :warn-on-reflection false

  :dependencies [[me.sneer/sneer-java-api "0.1.5"]
                 [me.sneer/crypto "0.1.5"]
                 [org.clojure/core.match "0.2.2"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.cognitect/transit-clj "0.8.281"]
                 [com.netflix.rxjava/rxjava-core "0.20.7"]
                 [org.clojure/java.jdbc "0.4.1"]
                 [org.clojure/core.cache "0.6.4"]]

  :profiles {:lean
             {:dependencies [[org.skummet/clojure-android "1.7.0-alpha5-r1" :use-resources true]]
              :exclusions [[org.clojure/clojure]]
              :omit-source true
              :skummet-skip-vars []
              :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
              :aot [sneer.commons
                    sneer.time
                    sneer.async
                    sneer.flux
                    sneer.io
                    sneer.restartable
                    sneer.party-impl
                    sneer.tuple.protocols
                    sneer.rx-macros
                    sneer.rx
                    sneer.tuple-base-provider
                    sneer.tuple.persistent-tuple-base
                    sneer.tuple.space
                    sneer.tuple.queue
                    sneer.tuple.transmitter
                    sneer.admin
                    sneer.contact
                    sneer.contacts
                    sneer.message-subs
                    sneer.conversation
                    sneer.convo
                    sneer.convo-summarization
                    sneer.convos
                    sneer.notifications
                    sneer.impl
                    sneer.keys
                    sneer.party
                    sneer.serialization
                    sneer.networking.udp
                    sneer.networking.client
                    sneer.impl.CoreLoader
                    sneer.main]
              :uberjar-exclusions [#"META-INF/DUMMY.SF"
                                   #"^org/(apache|bouncycastle|json|msgpack)"
                                   #"^lib/commons-codec"
                                   #"^cljs"
                                   #"^clojure/test/"
                                   #"^javassist"]
              :jvm-opts ["-Dclojure.compile.ignore-lean-classes=true"]
              :plugins [[org.skummet/lein-skummet "0.2.2"]]}


             :dev
             {:aot [sneer.impl.CoreLoader]
              :dependencies [[org.clojure/clojure "1.7.0"]
                             [midje "1.7.0"]
                             [org.xerial/sqlite-jdbc "3.8.11.1"]
                             [org.clojure/core.cache "0.6.4"]]
              :plugins [[lein-midje "3.1.3"]]}}


  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :test-paths ["src/test/clojure"])
