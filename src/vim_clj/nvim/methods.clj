(ns vim-clj.nvim.methods
  (:require [vim-clj.inspect :as inspect]
            [vim-clj.nvim.core :as nvim :refer [api-call]]
            [vim-clj.nrepl.core :as nrepl]
            [cljfmt.core :as fmt]
            [medley.core :as m]
            [clojure.string :as str])
  (:import (java.util.jar JarFile)))

(defonce should-shutdown (atom false))

(defn shutdown []
  (reset! should-shutdown true))

(defn clj-file-ns [file]
  (try
    (inspect/read-ns-name file)
    (catch Exception _ nil)))

(defn- stringify-keys [res]
  (m/map-keys name res))

(defn- read-from-jar
  ([path-spec]
   (let [[_ _ path] (str/split path-spec #":")
         [jar-file entry-path] (str/split (str path) #"!")]
     (when (str/includes? path ".jar")
       (read-from-jar jar-file entry-path))))
  ([jarfile path]
   (let [path (str/replace path #"^/" "")
         jf (JarFile. jarfile)
         entry (.getEntry jf path)]
     (when entry
       (slurp (.getInputStream jf entry))))))

(defn jump-to-symbol
  ([]
   (let [nrepl-scope (api-call call-function "getcwd" [])
         ns (clj-file-ns (api-call call-function "expand" ["%:p"]))
         sym (api-call call-function "expand" ["<cword>"])]
     (jump-to-symbol nrepl-scope ns sym)))
  ([nrepl-scope ns sym]
   (nrepl/with-scope nrepl-scope
     (let [{:keys [file entry column line]} (->> (nrepl/symbol-info ns sym)
                                                 nrepl/symbol-info->location)
           bufpath (api-call call-function "expand" ["%:p"])]
       (cond
         (and file entry (str/includes? file ".jar")) (do
                                                        (nvim/edit-zip file entry)
                                                        (nvim/setpos line column))
         (= file bufpath) (nvim/setpos line column)

         file (do
                (api-call command (str "keepjumps edit " file))
                (nvim/setpos line column))
         :else (nvim/err-writeln (str "Can't find source for " sym)))))))

(defn ns-eval [nrepl-scope ns code]
  (nrepl/with-scope nrepl-scope
    (stringify-keys (nrepl/ns-eval ns code))))

(defn format-code [lnum lcount]
  (when (not (->> (api-call call-function "mode" []) (re-find #"[iR]")))
    (let [[line1 line2]  [lnum (dec (+ lnum lcount))]
         lines          (api-call call-function "getline" [line1 line2])
         formatted-code (try (fmt/reformat-string (clojure.string/join "\n" lines))
                             (catch Exception _ nil))]
     (when formatted-code
       (nvim/replace-lines line1 line2 formatted-code))
     formatted-code)))

(defn symbol-info [nrepl-scope ns symbol]
  (nrepl/with-scope nrepl-scope
    (let [{:keys [name ns arglists-str doc]} (nrepl/symbol-info ns symbol)]
      (doseq [line [(when (and name ns) (str ns "/" name))
                    arglists-str
                    doc]]
        (when line (nvim/out-writeln line))))))

(defn connect-nrepl [conn-str]
  (future
      (if-let [conn-map (nrepl/str->conn-map conn-str)]
        (try
          (do (nrepl/manual-connect! conn-map)
              (nvim/out-writeln "Connected!"))
          (catch Exception ex
            (nvim/out-writeln (str "Error: " (.getMessage ex)))))
        (nvim/out-writeln (str "Bad address: " conn-str))))
  nil)

(defn- print-nrepl-result [{:keys [out value err ex root-ex]}]
  (doseq [str [err ex root-ex]]
    (when str (nvim/err-writeln str)))
  (when out (nvim/out-writeln out))
  (doseq [str value]
    (when str (nvim/out-writeln str))))

(defn- nrepl-eval [input-fn nrepl-scope ns]
  (let [history (vec (api-call get-var "VIM_CLJ_NREPL_HISTORY"))]
    (nrepl/with-scope nrepl-scope
      (let [res (nvim/with-history "@" history
                  (let [code (input-fn (str ns "=> "))]
                    (when (not-empty code)
                      (api-call set-var "VIM_CLJ_NREPL_HISTORY" (conj history code))
                      (nrepl/ns-eval ns code))))]
        (print-nrepl-result res)))))

(defn ping [] "pong")

(defn nrepl-require-ns [nrepl-scope ns]
  (println "require")
  (nrepl/with-scope nrepl-scope
    (let [code (str `(require [~(symbol ns) :reload]))]
      (nrepl/ns-eval ns code)
      (nvim/out-writeln code))))

(def nrepl-eval-prompt (partial nrepl-eval nvim/read-input))
(def nrepl-eval-cmdline (partial nrepl-eval nvim/read-input-cmline))

(defn- call-api-method [f msg]
  (let [{:keys [args]} (nvim/msg->map msg)]
    (try (apply f args)
         (catch Exception e {"ex" (.toString e)
                             "stacktrace" (map #(.toString %) (.getStackTrace e))}))))

(defn- defapimethod [name method-fn]
  (nvim/register-method! name (partial call-api-method method-fn)))

(defn register-methods! []
  (let [methods {"shutdown"           #'shutdown
                 "clj-file-ns"        #'clj-file-ns
                 "ns-eval"            #'ns-eval
                 "format-code"        #'format-code
                 "symbol-info"        #'symbol-info
                 "connect-nrepl"      #'connect-nrepl
                 "nrepl-eval-prompt"  #'nrepl-eval-prompt
                 "nrepl-eval-cmdline" #'nrepl-eval-cmdline
                 "ping"               #'ping
                 "jump-to-symbol"     #'jump-to-symbol
                 "require-ns"         #'nrepl-require-ns}]
    (doseq [[name f] methods] (defapimethod name f))))
