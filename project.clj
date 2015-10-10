(defproject kiln "0.1.0-SNAPSHOT"
  :description "静态网站生成器"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [stencil "0.5.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-time "0.11.0"]
                 [seesaw "1.4.5"]
                 [markdown-clj "0.9.74"]]
  :main ^:skip-aot kiln.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
