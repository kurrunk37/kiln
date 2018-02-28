(defproject kiln "0.2.7"
  :description "静态博客生成器"
  :url "https://github.com/huzhengquan/kiln"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [stencil "0.5.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-time "0.14.2"]
                 [clj-rss "0.2.3"]
                 [markdown-clj "1.0.2"]]
  :main kiln.core
  :jar-exclusions [#"\.swp|\.swo|\.DS_Store"]
  :profiles {:uberjar {:aot :all}})
