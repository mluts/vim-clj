(ns vim-clj.nvim.methods
  (:require [vim-clj.inspect :as inspect]
            [vim-clj.nvim.core :as nvim :refer [api-call]]
            [vim-clj.nrepl.core :as nrepl]
            [cljfmt.core :as fmt]
            [medley.core :as m]))

(defonce should-shutdown (atom false))

(defn shutdown []
  (reset! should-shutdown true))

(defn clj-file-ns [file]
  (try
    (inspect/read-ns-name file)
    (catch Exception _ nil)))

(defn- stringify-keys [res]
  (m/map-keys name res))

(defn ns-eval [nrepl-scope ns code]
  (binding [nrepl/*connection-scope* nrepl-scope]
    (stringify-keys (nrepl/ns-eval ns code))))

(defn format-code [lnum lcount]
  (let [[line1 line2]  [lnum (dec (+ lnum lcount))]
        lines          (api-call call-function "getline" [line1 line2])
        formatted-code (try (fmt/reformat-string (clojure.string/join "\n" lines))
                            (catch Exception _ nil))]
    (when formatted-code
      (nvim/replace-lines line1 line2 formatted-code))
    formatted-code))

(defn symbol-info [nrepl-scope ns symbol]
  (binding [nrepl/*connection-scope* nrepl-scope]
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
    (binding [nrepl/*connection-scope* nrepl-scope]
      (let [res (nvim/with-history "@" history
                  (let [code (input-fn (str ns "=> "))]
                    (when (not-empty code)
                      (api-call set-var "VIM_CLJ_NREPL_HISTORY" (conj history code))
                      (nrepl/ns-eval ns code))))]
        (print-nrepl-result res)))))

(defn ping [] "pong")

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
                 "ping"               #'ping}]
    (doseq [[name f] methods] (defapimethod name f))))
