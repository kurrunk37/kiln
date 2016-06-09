(defproject kiln "0.2.4"
  :description "静态博客生成器"
  :url "https://github.com/huzhengquan/kiln"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
								 [stencil "0.5.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-time "0.11.0"]
                 [clj-rss "0.2.3"]
                 [markdown-clj "0.9.87"]]
  :main ^:skip-aot kiln.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
