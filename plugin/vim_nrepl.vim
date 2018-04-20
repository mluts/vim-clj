let s:plugin_dir = expand('<sfile>:p:h:h')
let s:jar_path = join([s:plugin_dir, 'target', 'uberjar', 'vim-nrepl.jar'], '/')
let g:vim_nrepl_is_running = 0
let g:vim_nrepl_uberjar = 0
let g:vim_nrepl_channel = 0

function! vim_nrepl#is_running()
  return g:vim_nrepl_is_running
endfunc

function! vim_nrepl#on_exit()
  echo 'VimNrepl exited'
endfunc

function! vim_nrepl#start()
  if vim_nrepl_is_running() | return | endif

  if g:vim_nrepl_uberjar
    let g:vim_nrepl_channel = jobstart(['java', '-jar', s:jar_path], {'rpc': v:true, 'on_exit': function('VimNreplExit')})
  else
    let cmd = 'cd ' . s:plugin_dir . ' && lein run'
    let g:vim_nrepl_channel = jobstart(cmd, {'rpc': v:true, 'on_exit': function('VimNreplExit')})
  endif

  if g:vim_nrepl_channel <= 0
    echo 'VimNrepl was not started'
  endif
endfunc

function! vim_nrepl#request(cmd, ...)
  if g:vim_nrepl_is_running
    return call(function('rpcrequest'), [g:vim_nrepl_channel, a:cmd] + a:000)
  endif
endfunc

function! vim_nrepl#notify(cmd, ...)
  if g:vim_nrepl_is_running
    return call(function('rpcnotify'), [g:vim_nrepl_channel, a:cmd] + a:000)
  endif
endfunc

function! vim_nrepl#stop()
  let g:vim_nrepl_is_running = 0
  call VimNreplRequest('shutdown')
endfunc

function! vim_nrepl#ns()
  return vim_nrepl#request('clj-file-ns', expand('%'))
endfunc

command! -bar VimNreplStart call vim_nrepl#start()
command! -bar VimNreplStop call vim_nrepl#stop()

augroup vim_nrepl
  au!
  au VimLeave * call vim_nrepl#stop()
augroup END
