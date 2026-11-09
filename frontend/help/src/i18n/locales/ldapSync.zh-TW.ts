export default {
  ldapSyncGuide: {
    pageTitle: 'LDAP 同步記錄',
    crumb: '管理中心 · 稽核日誌',
    intro:
      'LDAP 同步記錄列出本環境的每一次 LDAP / AD 同步：用戶檔案和虛擬群組名單，含狀態、計數和該次同步留下的訊息。頁面唯讀。System Administrator、Technical Lead、Auditor 看到的是同一份記錄。',
    flowTitle: '操作順序',
    flow1: '開啟管理中心 → 稽核日誌 → LDAP 同步記錄。',
    flow2: '在欄頭把「狀態」篩成「失敗」或「部分成功」，或在搜尋框輸入 AD 群組名稱。',
    flow3: '看「訊息」。群組同步加了誰、移除了誰，到「管理後台稽核日誌」查操作人 LDAP_SYNC_JOB。',
    listTitle: '清單顯示什麼',
    listBody: '每次同步一列，最新的在上。同步開始時寫入（進行中），結束時改為成功、部分成功或失敗。',
    listLead: '欄位目錄 — LDAP 同步記錄清單（中文介面欄名：類型、狀態、開始時間、耗時、讀取數、新增、更新、失敗筆數、略過筆數、訊息）。',
    listFigure: '以 Technical Lead 開啟 LDAP 同步記錄（英文介面）：稽核日誌選單、搜尋 No virtual group、訊息欄。沒有發起同步的按鈕。',
    fType:
      '用戶（全量）、用戶（增量）從目錄讀取用戶檔案，只在 System Administrator 發起時執行。增量只讀最近一次狀態為「成功」的用戶同步（全量或增量）之後改過的條目，「部分成功」的用戶同步和群組同步都不算；還沒有這樣的記錄時按用戶（全量）執行並記錄。群組（全量）、群組（增量）把每份虛擬群組名單對齊到它對應的 AD 群組，兩者做的事相同。啟動時和按排程執行的同步記為群組（增量）。',
    fStatus: '進行中、成功、部分成功或失敗（含義見「各狀態的含義」）。可在欄頭篩選。',
    fStarted: '同步開始的時間，按瀏覽器所在時區顯示。',
    fDuration: '本次耗時，如 12.3s、2m 05s。短橫：還沒有結束，或該類型不記錄耗時。',
    fFetched: '本次從目錄讀取的條目數。',
    fAddedUpdated: '本次按目錄新建、更新的用戶帳號數。短橫：還沒有結束，或是沒有這兩個數的舊記錄。',
    fFailedItems: '有員工編號、但沒能儲存的目錄條目，例如資料庫出錯；哪些條目見管理中心日誌。同步不會因此中斷；對狀態的影響見「各狀態的含義」裡的部分成功。',
    fSkipped:
      '沒有員工編號的目錄條目。按設計這類帳號不入庫：不算失敗，也不影響狀態；「訊息」裡 Skipped without an employee ID (not stored) 後面是略過的筆數。群組同步裡一個帳號在兩個對應的 AD 群組中會算兩次，與「讀取數」一致。短橫：沒有這個數的舊記錄。',
    fMessage:
      '同步因錯誤中止時：原樣記錄的錯誤文字，超過 1000 個字元截斷。群組同步：Not read, lists unchanged、Read incompletely, nobody removed、No members found (missing or empty AD group) 等說明，後面接相關的 AD 群組，多條之間以分號隔開。讀過目錄的同步：Skipped without an employee ID (not stored) 後面接略過的筆數；用戶同步：Could not be stored 後面接沒能儲存的筆數。沒有要說明的內容時為空。訊息太長時儲存格裡顯示不全，滑鼠懸停可看全文，每條說明一行。',
    statusTitle: '各狀態的含義',
    statusLead: '欄位目錄 — 「狀態」的取值（中文介面：進行中、成功、部分成功、失敗）。',
    sRunning: '同步已開始，還沒有結束。',
    sSucceeded:
      '用戶同步：目錄讀完了，有員工編號的條目都儲存了。群組同步：每個對應的 AD 群組都完整讀到，有員工編號的成員也都儲存了。沒有員工編號的條目記為略過（略過筆數），不影響狀態。AD 群組為空或不存在（No members found）、為保住 System Administrators 保留了 AD 成員（Kept the AD members of …）、或者沒有任何名單對應 AD 群組，都仍算成功。',
    sPartial:
      '用戶同步：目錄讀完了，有員工編號的條目有的沒能儲存（失敗筆數；「訊息」：Could not be stored），其餘都儲存了。下一次用戶（增量）從最近一次「成功」的用戶同步開始讀，不從這一次開始。群組同步：部分對應的 AD 群組讀不出（Not read, lists unchanged），或只讀到一部分、成員沒能全部儲存（Read incompletely, nobody removed）。這些 AD 群組對應的名單不移除任何人；其餘 AD 群組照常同步。「訊息」裡列出相關的 AD 群組。',
    sFailed:
      '群組同步：對應的 AD 群組一個都讀不出，例如目錄連不上；所有名單都沒有變化，「訊息」在 Not read, lists unchanged 後面列出這些 AD 群組。用戶同步：目錄讀完了，但有員工編號的條目一筆都沒能儲存（新增、更新都是 0；「訊息」：Could not be stored），下一次用戶（增量）從最近一次「成功」的用戶同步開始讀。任何同步：因錯誤中止，「訊息」裡是錯誤文字。',
    scheduleTitle: '什麼時候同步',
    scheduleBody:
      '只有管理中心同時開啟 ldap.enabled 和 ldap.sync-enabled 兩個開關時才會同步。開關開啟後，管理中心啟動時執行一次群組同步，之後按 ldap.sync-cron 定時執行（預設每 2 小時，整點）。System Administrator 也可以呼叫介面手動發起四種類型中的任意一種，管理中心畫面上沒有入口。',
    scheduleLock:
      '同一時刻只有一次 LDAP 同步在跑，多個管理中心執行個體之間也是如此。啟動、定時或手動的同步正在進行時，再呼叫介面發起會被拒絕，回傳 HTTP 409 和「另一次 LDAP 同步正在進行，請等它結束後再發起。」。啟動或定時同步發現另一次同步正在進行時直接略過，不排隊補跑，管理中心日誌記一筆警告。同步跑多久就持鎖多久；同步途中管理中心停了，鎖最多 20 分鐘後釋放。持鎖用的 Redis 連不上時，呼叫介面發起會被拒絕，回傳 HTTP 503 和「LDAP 同步未啟動：同步鎖服務（Redis）無法連線。請稍後重試，或請平台維運檢查 Redis。」。',
    findTitle: '查找某次同步',
    findLead: '欄位目錄 — 工具列和欄頭。',
    fSearch:
      '中文介面為「搜尋類型、狀態、訊息或群組」。比對「訊息」和群組同步讀取的 AD 群組（AD 群組不單獨成欄），也比對類型、狀態的儲存代碼，如 FAILED、HERMES_AD_GROUP，不比對中文標籤。按 Enter 或點「搜尋」；清空搜尋框會重新載入。',
    fColumnMenu: '排序、篩選、左右移動或調整欄寬。「類型」「狀態」按標籤篩選。',
    fExport: '「匯出」：有勾選時把勾選的列匯出為 CSV，沒有勾選時匯出目前頁。',
    fRefresh: '「重新整理」：按目前搜尋和篩選重新載入。',
    fTotal: '「共 N 筆」：符合搜尋和篩選的記錄數。',
    accessTitle: '誰能開啟',
    accessBody: '能開啟本頁的人看到同一份記錄，不按團隊過濾。本頁沒有發起、停止或刪除同步的按鈕。',
    accessMessage: '「訊息」對所有角色原樣顯示。失敗資訊裡可能有 LDAP 主機名稱或 DN，不含密碼。',
    fRoleAdmin: '看到全部記錄。只有 System Administrator 能發起同步，且只能呼叫介面，畫面上沒有入口。',
    fRoleLead: '看到全部記錄，唯讀。',
    fRoleAuditor: '看到全部記錄，唯讀。',
    membersTitle: '加了誰、移除了誰',
    membersBody:
      '群組同步在本頁只記計數。它往虛擬群組名單裡加入或移除的每一個人，都在「管理後台稽核日誌」裡單獨一筆，操作人為 LDAP_SYNC_JOB。手動加入的成員不會被同步移除。',
    failTitle: '看起來不對時',
    failEmpty: '沒有記錄：本環境沒有開啟 LDAP 同步（ldap.enabled 和 ldap.sync-enabled 都要開啟），或還沒有執行過。關閉之前寫下的記錄仍留在清單裡。',
    failRunning: '開始很久仍是「進行中」：該次同步沒有結束，例如執行中管理中心重新啟動了。下一次同步會寫自己的記錄。',
    failFailed:
      '失敗：看「訊息」。Not read, lists unchanged 後面列出全部對應的 AD 群組，表示目錄完全讀不出；Could not be stored 且新增、更新都是 0，表示用戶同步讀完了目錄但一筆都沒能儲存，原因見管理中心日誌；其他內容是同步中止時的錯誤。群組同步會在下一個排程時間再執行；原因排除後，System Administrator 也可以發起一次。',
    failPartialStatus:
      '部分成功：群組同步時，「訊息」列出沒讀出或只讀到一部分的 AD 群組，它們對應的名單沒有移除任何人，下一次同步會再試。用戶同步時，有條目沒能儲存（Could not be stored），哪些條目見管理中心日誌；下一次用戶（增量）從最近一次「成功」的用戶同步重新讀。',
    failNotRead: 'Not read, lists unchanged：這些 AD 群組讀取失敗，它們對應的名單保留原有的 AD 成員。本次為部分成功；一個 AD 群組都沒讀出時為失敗。',
    failPartial: 'Read incompletely, nobody removed：這些 AD 群組只讀到一部分，或有成員沒能儲存。新成員照常加入，但對應的名單裡不移除任何人。本次為部分成功。',
    failNoMembers:
      'No members found (missing or empty AD group)：對應的 AD 群組不存在或沒有成員，它對應的名單會移除原有的 AD 成員。這條說明不影響狀態。到「虛擬群組管理」核對 AD Group（團隊為 Developer AD 群組、Technical Lead AD 群組）。',
    failKept:
      'Kept the AD members of …: removing them would leave no active member：只針對 System Administrators 群組——移除後該群組將沒有有效成員，本次保留了它的 AD 成員。這條說明不影響狀態。其他名單沒有這項保護，按 AD 的結果移除。',
    failBusy:
      '呼叫介面發起同步回傳 409「另一次 LDAP 同步正在進行…」：啟動、定時或手動的同步正在進行，等它結束後再發起。啟動或定時同步開始後的 1 分鐘內，即使已經結束，手動發起也會被拒絕。',
    failLockUnavailable:
      '呼叫介面發起同步回傳 503「LDAP 同步未啟動：同步鎖服務（Redis）無法連線…」：管理中心連不上存放同步鎖的 Redis，本次沒有發起任何同步。稍後重試；持續出現時請平台維運檢查 Redis。',
    failNoMapping: 'No virtual group maps an AD group：沒有任何名單對應 AD 群組，本次沒有讀取，狀態為成功。',
    failLoad: '載入 LDAP 同步記錄失敗：點「重新整理」重試。',
    failMenu: '稽核日誌下沒有「LDAP 同步記錄」：你的帳號不是 System Administrator、Auditor，也不是有效團隊的 Technical Lead。',
  },
}
