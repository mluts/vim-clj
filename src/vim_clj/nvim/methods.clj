(ns vim-clj.nvim.methods
  (:require [vim-clj.inspect :as inspect]
            [vim-clj.nvim.core :as nvim]
            [vim-clj.nrepl.core :as nrepl]
            [cljfmt.core :as fmt]))

(defonce should-shutdown (atom false))

(defn- select-str-keys [m key-seq]
  (->> (select-keys m key-seq)
       (map (juxt (comp name key) (comp str val)))
       (into {})))

(defn shutdown [& _]
  (reset! should-shutdown true))

(defn clj-file-ns [msg]
  (let [{:keys [args]} (nvim/msg->map msg)]
    (try
      (inspect/read-ns-name (first args))
      (catch Exception _ nil))))

(defn ns-eval [msg]
  (let [{:keys [args]} (nvim/msg->map msg)
        [nrepl-scope ns code] args
        eval-res (delay (nrepl/ns-eval ns code))]
    (when (and nrepl-scope ns code @eval-res)
      (binding [nrepl/*connection-scope* nrepl-scope]
        (select-str-keys @eval-res [:value :err :out])))))

(defn format-code [msg]
  (let [{:keys [args]} (nvim/msg->map msg)
        code (first args)]
    (when code
      (try (fmt/reformat-string (str code))
           (catch Exception _ nil)))))

(defn symbol-info [msg]
  (let [{:keys [args]} (nvim/msg->map msg)
        [nrepl-scope ns symbol] args]
    (when (and nrepl-scope ns symbol)
      (binding [nrepl/*connection-scope* nrepl-scope]
        (let [{:keys [name ns arglists-str doc]} (nrepl/symbol-info ns symbol)]
          (doseq [line [(when (and name ns) (str ns "/" name))
                        arglists-str
                        doc]]
            (when line (nvim/out-writeln line))))))))

(defn connect-nrepl [msg]
  (let [{:keys [args]} (nvim/msg->map msg)
        [conn-str] args]
    (future
      (if-let [conn-map (nrepl/str->conn-map conn-str)]
        (try
          (do (nrepl/manual-connect! conn-map)
              (nvim/out-writeln "Connected!"))
          (catch Exception ex
            (nvim/out-writeln (str "Error: " (.getMessage ex)))))
        (nvim/out-writeln (str "Bad address: " conn-str))))
    nil))

(defn- nrepl-eval [input-fn msg]
  (let [{:keys [args]} (nvim/msg->map msg)
        [nrepl-scope ns] args
        code (delay (input-fn (str ns "=> ")))
        eval-res #(select-str-keys (nrepl/ns-eval ns @code) [:value :err :out])]
    (when (and nrepl-scope ns)
      (let [history (vec (nvim/get-var "VIM_CLJ_NREPL_HISTORY"))
            result (binding [nrepl/*connection-scope* nrepl-scope]
                     (nvim/with-history "@" history eval-res))]

        (nvim/set-var "VIM_CLJ_NREPL_HISTORY" (conj history @code))
        result))))

(def nrepl-eval-prompt (partial nrepl-eval nvim/read-input))
(def nrepl-eval-cmdline (partial nrepl-eval nvim/read-input-cmline))

(defn register-methods! []
  (let [methods {"shutdown"           #'shutdown
                 "clj-file-ns"        #'clj-file-ns
                 "ns-eval"            #'ns-eval
                 "format-code"        #'format-code
                 "symbol-info"        #'symbol-info
                 "connect-nrepl"      #'connect-nrepl
                 "nrepl-eval-prompt"  #'nrepl-eval-prompt
                 "nrepl-eval-cmdline" #'nrepl-eval-cmdline}]
    (doseq [[m f] methods] (nvim/register-method! m f))))
