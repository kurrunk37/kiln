(ns kiln.core
  (:gen-class)
  (:use stencil.core)
  (:require [clojure.data.json :as json]
            [markdown.core :as markdown]
            [clj-time.coerce :as timec]
            [clj-time.format :as timef]
            [seesaw.core :refer [native! frame show! config!]]))

#_(def workdir "/Users/hu/dev/kiln/test/sync/")
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

(defn wrap-author
    "元数据的作者，转成HTML"
    [meta-map]
    (if (and (contains? meta-map :author)
             (not-empty (:author meta-map))
             (contains? dict (:author meta-map)))
        (let [author (:author meta-map)]
            (merge meta-map
                {:author-html (str "<a href=\"" (get dict author) "\">" author "</a>")}))
        meta-map))

(defn wrap-ad
    "元数据的广告，转成HTML"
    [meta-map]
    (if (and (contains? meta-map :ad)
             (not-empty (:ad meta-map))
             (contains? dict (:ad meta-map)))
        (let [ad (:ad meta-map)]
            (merge meta-map
                {:ad-html (str "<a href=\"" (get dict ad) "\"><img src=\"" ad "\" style=\"width:100%;height:80px;\" /></a>")}))
        meta-map))

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
                    (wrap-author (wrap-ad (into {} (for
                         [[_ k v] (map #(re-matches #"^([\w]+)[\s]*:[\s]*(.+)$" %) meta-lines)]
                         [(keyword (clojure.string/lower-case k)) v])))))
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

(def win-f
    (frame
        :title "kiln"
        :width 240
        :height 180
        :content "准备中……"
        :on-close :exit))
(show! win-f)

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (doseq [md-file (only-markdown (file-seq (clojure.java.io/file (str workdir "/src"))))]
    (generation md-file)
    (Thread/sleep 500)
    (config! win-f :content (str (.getName md-file) " --- ok!")))
  (Thread/sleep 1000)
  (config! win-f :content " HTML已生成，接下来可以同步上线了！"))
