(ns vim-clj.nvim.methods
  (:require [vim-clj.inspect :as inspect]
            [vim-clj.nvim.core :as nvim]
            [vim-clj.nrepl.core :as nrepl]
            [cljfmt.core :as fmt]))

(defonce should-shutdown (atom false))

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
        (as-> (select-keys @eval-res [:value :err :out]) $
         (map (juxt (comp name key) val) $)
         (into {} $))))))

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
        (as-> (nrepl/symbol-info ns symbol) $
         (select-keys $ [:doc :file :name :resource :ns :line :column :arglists-str :macro])
         (map (juxt (comp name key) (comp str val)) $)
         (into {} $))))))

(defn register-methods! []
  (let [
        methods {"shutdown"     #'shutdown
                 "clj-file-ns"  #'clj-file-ns
                 "ns-eval"      #'ns-eval
                 "format-code"  #'format-code
                 "symbol-info"  #'symbol-info}]
    (doseq [[m f] methods] (nvim/register-method! m f))))
