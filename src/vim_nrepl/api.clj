(ns vim-nrepl.api)

(defn msg->map [[msg-type argc method args]]
  {:msg-type  msg-type
   :argc      argc
   :method    method
   :args      args})
