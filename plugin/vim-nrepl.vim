let s:plugin_dir = expand('<sfile>:p:h:h')
let s:jar_path = join([s:plugin_dir, 'target', 'uberjar', 'vim-nrepl.jar'], '/')
let g:vim_nrepl_is_running = 0
let g:vim_nrepl_uberjar = 0
let g:vim_nrepl_channel = 0

function! VimNreplIsRunning()
  return g:vim_nrepl_is_running
endfunc

function! VimNreplExit(...)
  echo 'VimNrepl exited'
endfunc

function! VimNreplStart()
  if VimNreplIsRunning() | return | endif

  if g:vim_nrepl_uberjar
    let g:vim_nrepl_channel = jobstart(['java', '-jar', s:jar_path], {'rpc': v:true, 'on_exit': function('VimNreplExit')})
  else
    let cmd = 'cd ' . s:plugin_dir . ' && lein run'
    echo cmd
    let g:vim_nrepl_channel = jobstart(cmd, {'rpc': v:true, 'on_exit': function('VimNreplExit')})
  endif

  if g:vim_nrepl_channel <= 0
    echo 'VimNrepl was not started'
  endif
endfunc

function! VimNreplRequest(cmd, ...)
  call VimNreplStart()

  if g:vim_nrepl_is_running
    return rpcrequest(g:vim_nrepl_channel, a:cmd, a:000)
  endif
endfunc

function! VimNreplStop()
  call VimNreplRequest('shutdown')
endfunc

command! -bar VimNreplStart call VimNreplStart()

augroup vim_nrepl
  au!
  au VimLeave * call VimNreplStop()
augroup END
