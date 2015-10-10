(ns kiln.core
  (:gen-class)
  (:use stencil.core)
  (:require [clojure.data.json :as json]
            [markdown.core :as markdown]
            [clj-time.coerce :as timec]
            [clj-time.format :as timef]))

(def workdir "/Users/hu/dev/kiln/test/sync/")
(def workdir "./")

(defn only-markdown
    " 过滤出目录中的 markdown 文件"
    [file-s]
    (filter #(and (.isFile %) (re-find #".md$" (.getName %))) file-s))

(def dict
    (if (.exists (clojure.java.io/file (str workdir "/map.clj")))
        (read-string (slurp (str workdir "/map.clj")))
        {}
        ))

(defn generation
    "生成 html 文件"
    [md-file]
    (with-open [rdr (clojure.java.io/reader (.getPath md-file))]
        (let [lines (line-seq rdr)
              meta-lines (take-while #(re-matches #"^[\w\s]+:(.+)$" %) lines)
              md-string (clojure.string/join "\n" (drop (count meta-lines) lines))
              meta-map
                (merge {:template "weixin"
                        :content (markdown/md-to-html-string md-string)
                        :date (timef/unparse (timef/formatter "yyyy-MM-dd") (timec/from-long (.lastModified md-file)))
                        }
                    (into {} (for
                         [[_ k v] (map #(re-matches #"^([\w]+)[\s]*:[\s]*(.+)$" %) meta-lines)]
                         [(keyword (clojure.string/lower-case k)) v])))
              html-file (str "/html/" (clojure.string/replace (.getName md-file) #".md$" ".html"))]
            (spit (str workdir html-file)
                (render-string
                    (slurp (str workdir "/template/" (:template meta-map) ".html"))
                    (merge meta-map
                        {:dict (json/write-str
                                 (into {} (filter
                                    #(contains?
                                        (set (vals (select-keys meta-map [:ad :author])))
                                        (first %))
                                    dict)))}
                    )))
            (println html-file)
            )))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (doseq [md-file (only-markdown (file-seq (clojure.java.io/file (str workdir "/src"))))]
    (generation md-file))
  )
