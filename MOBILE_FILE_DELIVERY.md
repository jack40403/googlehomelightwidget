# 快速傳檔到手機

需要在外面立刻從手機取得 APK 或其他檔案時，使用 Tailscale Serve。

1. 在電腦的檔案所在資料夾啟動本機 HTTP 伺服器：
   `py -3 -m http.server 8765 --bind 127.0.0.1`
2. 建立只限 Tailnet 的 HTTPS 代理：
   `tailscale serve --bg 8765`
3. 在手機開啟 Tailscale 並連線，然後開啟 `tailscale serve status` 顯示的網址，加上檔名。
4. 完成後停止對外提供：
   `tailscale serve --https=443 off`

Tailscale Serve 不會公開到網際網路，只有登入相同 Tailnet 的裝置可以存取。手機若連不上，先確認 Tailscale 顯示已連線，並關閉 Android 對 Tailscale 的電池最佳化。
