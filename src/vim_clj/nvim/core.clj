(ns vim-clj.nvim.core
  (:require [neovim-client.1.api :as nvim-api]
            [neovim-client.1.api.buffer]
            [neovim-client.1.api.window]
            [neovim-client.nvim :as nvim-client]
            [neovim-client.message :as msg]
            [clojure.string :as str]))

(defonce nvim (atom {}))

(defonce is-running-var "vim_clj_is_running")
(defonce channel-var "vim_clj_channel")

(defmacro api-call [method & args]
  (let [f (symbol "neovim-client.1.api" (name method))]
    `((var ~f) @nvim ~@args)))

(defn msg->map [msg]
  {:msg-type  (msg/msg-type msg)
   :id        (msg/id msg)
   :method    (msg/method msg)
   :args      (msg/params msg)})

(defn register-method! [m f]
  (nvim-client/register-method! @nvim m f))

(defn connect-to-stdin! []
  (reset! nvim (nvim-client/new 1)))

(defn connect-to-port! [port]
  (reset! nvim (nvim-client/new 1 "127.0.0.1" (int port))))

(defn set-is-running [val]
  (api-call set-var-async is-running-var (int val) nil))

(defn set-channel-var []
  (nvim-api/get-api-info-async @nvim (fn [[channel]] (api-call set-var-async channel-var channel nil))))

(defn out-writeln [msg]
  (nvim-api/out-write @nvim (str msg "\n")))

(defn err-writeln [msg]
  (nvim-api/err-writeln @nvim msg))

(defn get-history [hist-name]
  {:pre [(string? hist-name)]}
  (let [first-hist-n 1
        last-hist-n (api-call call-function "histnr" [hist-name])
        get-hist-call (fn [n] ["nvim_call_function" ["histget" [hist-name n]]])
        [results error] (nvim-api/call-atomic @nvim (map get-hist-call (range first-hist-n (inc last-hist-n))))]
    (if error
      (throw (Exception. (str "Failed to get history for " hist-name ". " (last error))))
      results)))

(defn reset-history [hist-name hist-seq]
  (nvim-api/call-atomic @nvim
                        (cons ["nvim_call_function" ["histdel" [hist-name]]]
                              (map (fn [entry]
                                     ["nvim_call_function" ["histadd" [hist-name entry]]]) hist-seq))))

(defn swap-history [hist-name hist-seq]
  (let [old-history (get-history hist-name)]
    (reset-history hist-name hist-seq)
    old-history))

(defmacro with-history [hist-name hist-seq & body]
  `(let [old-history# (swap-history ~hist-name ~hist-seq)
         res# (do ~@body)]
     (reset-history ~hist-name old-history#)
     res#))

(defn read-input [prompt]
  (api-call call-function "input" [prompt]))

(defn read-input-cmline [prompt]
  (let [cedit (nvim-api/get-option @nvim "cedit")
        [results error] (api-call call-atomic [["nvim_input" [cedit]]
                                               ["nvim_call_function" ["input" [prompt]]]])]
    (when-not error
      (second results))))

(defn current-bufnr []
  (->> (api-call get-current-buf) :data first))

(defmacro buf-call [method & args]
  (let [f (symbol "neovim-client.1.api.buffer" (name method))]
    `((var ~f) @nvim (current-bufnr) ~@args)))

(defn replace-lines [line1 line2 new-lines]
  (let [[first-line & lines]  (clojure.string/split-lines new-lines)
        delete-line-cmd       ["nvim_command"       [(format "%d,%ddelete" (inc line1) line2)]]
        setline-cmd           ["nvim_call_function" ["setline" [line1 first-line]]]
        append-lines          ["nvim_call_function" ["append" [line1 (vec lines)]]]
        commands              (filter identity [(when (< line1 line2) delete-line-cmd)
                                                setline-cmd
                                                (when (not-empty lines) append-lines)])]
    (api-call call-atomic (vec commands))))

(defn show-in-pseudo-file [name content]
  (let [bufnr (api-call call-function "bufnr" [name])]
    (if (<= 0 bufnr)
      (api-call command (str "buffer " bufnr))
      (do
        (api-call command (str "new " name))
        (buf-call set-option "buftype" "nofile")))
    (buf-call set-option "modifiable" true)
    (api-call command "1,$delete")
    (replace-lines 1 1 content)
    (buf-call set-option "modifiable" false)))

(defn- save-context-mark []
  (api-call command "normal! m'"))

(defn setpos [line col]
  (api-call call-function "setpos" [".", [0 line col 0]]))

(defn edit-zip [file entry]
  (let [bufname (str "zipfile:" file "::" (str/replace entry #"^/" ""))
        bufnr (api-call call-function "bufnr" [bufname])]
    (if (<= 0 bufnr)
      (api-call command (str "buf " bufnr))
      (api-call command (str "edit " bufname)))))

