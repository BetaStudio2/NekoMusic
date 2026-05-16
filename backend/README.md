确保 nginx 反代中允许的请求头 `Authorization,Content-Type,Accept,Origin,User-Agent,DNT,Cache-Control,X-Mx-ReqToken,X-Requested-With,Expires,If-Modified-Since,Pragma`

后端提供 `GET /detail/{id}`，返回含歌曲 meta 与正文的 HTML（无需 JS）。Nginx 反代见项目根目录 `nginx.conf`。
