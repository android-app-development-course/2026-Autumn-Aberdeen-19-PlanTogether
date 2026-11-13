# 生成一份演示数据：一个组长 + 一个队友 + 一趟有地点/链接/投票/清单/评论的杭州行程
# 用法：powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1
$ErrorActionPreference = "Stop"
$Base = "http://127.0.0.1:8080"

$DemoEmail = "demo@plantogether.app"
$DemoPassword = "password123"
$MateEmail = "mate@plantogether.app"
$MatePassword = "password123"

function Post-Json {
    param([string]$Path, $Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Body -Depth 8 -Compress))
    return Invoke-RestMethod -Method Post -Uri "$Base$Path" -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bytes
}

function Put-Json {
    param([string]$Path, $Body, [string]$Token)
    $headers = @{ Authorization = "Bearer $Token" }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Body -Depth 8 -Compress))
    return Invoke-RestMethod -Method Put -Uri "$Base$Path" -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bytes
}

function Get-Api {
    param([string]$Path, [string]$Token)
    $headers = @{ Authorization = "Bearer $Token" }
    return Invoke-RestMethod -Method Get -Uri "$Base$Path" -Headers $headers
}

function Ensure-User {
    param([string]$Email, [string]$Password, [string]$Name)
    try {
        $register = @{ email = $Email; password = $Password; displayName = $Name }
        return Post-Json "/api/auth/register" $register
    } catch {
        return Post-Json "/api/auth/login" @{ email = $Email; password = $Password }
    }
}

$owner = Ensure-User $DemoEmail $DemoPassword "组长小A"
$mate = Ensure-User $MateEmail $MatePassword "队友小B"

$existing = @(Get-Api "/api/trips" $owner.token | Where-Object { $_.title -eq "周末去杭州（Demo）" })
if ($existing.Count -gt 0) {
    $trip = $existing[0]
    Write-Host "演示行程已存在，跳过创建：id=$($trip.id)" -ForegroundColor Yellow
} else {
    $trip = Post-Json "/api/trips" @{
        title        = "周末去杭州（Demo）"
        destination  = "杭州"
        startDate    = "2026-11-07"
        endDate      = "2026-11-08"
        groupSize    = 4
        budgetTarget = 2000
    } $owner.token
    Write-Host "已创建行程 id=$($trip.id) 邀请码=$($trip.inviteCode)" -ForegroundColor Green

    # 队友加入
    $null = Post-Json "/api/trips/join" @{ inviteCode = $trip.inviteCode } $mate.token

    # 地点（两天，含坐标、预算、备注、备选）
    $places = @(
        @{ name = "西湖断桥"; address = "杭州市西湖区北山街"; latitude = 30.2587; longitude = 120.1489;
           date = "2026-11-07"; startTime = "09:00"; endTime = "11:00"; budget = 0;
           notes = "早上人少，适合拍照"; mapLink = "https://uri.amap.com/marker?position=120.1489,30.2587" },
        @{ name = "楼外楼（孤山路店）"; address = "西湖区孤山路30号"; latitude = 30.2525; longitude = 120.1445;
           date = "2026-11-07"; startTime = "12:00"; endTime = "13:30"; budget = 480; stayLength = "1.5 小时";
           notes = "需要提前订位"; backupOption = "知味观（仁和路）" },
        @{ name = "灵隐寺"; address = "西湖区灵隐路法云弄1号"; latitude = 30.2408; longitude = 120.1010;
           date = "2026-11-07"; startTime = "15:00"; endTime = "17:30"; budget = 75; notes = "门票 75 元含飞来峰" },
        @{ name = "河坊街"; address = "上城区河坊街"; latitude = 30.2420; longitude = 120.1690;
           date = "2026-11-08"; startTime = "10:00"; budget = 200; notes = "买点伴手礼" }
    )
    $placeIds = @()
    foreach ($p in $places) { $placeIds += (Post-Json "/api/trips/$($trip.id)/places" $p $mate.token).id }

    # 链接收藏
    $null = Post-Json "/api/trips/$($trip.id)/links" @{
        url = "https://www.bilibili.com/video/BV1demo"; title = "杭州两日游路线参考"; source = "B站";
        tags = "路线,攻略"; notes = "第三天可加西溪湿地"
    } $mate.token
    $null = Post-Json "/api/trips/$($trip.id)/links" @{
        url = "https://www.dianping.com/shop/demo"; title = "知味观（仁和路总店）"; source = "大众点评";
        tags = "餐厅,备选"; notes = "人均 80 左右，排队短"
    } $owner.token

    # 群决策
    $decision = Post-Json "/api/trips/$($trip.id)/decisions" @{
        title = "周六晚上吃什么"; notes = "都离西湖不远，走路 10 分钟内"; options = @("知味观", "新白鹿", "回酒店点外卖")
    } $owner.token
    $opt1 = $decision.options[0].id
    $opt2 = $decision.options[1].id
    $null = Post-Json "/api/trips/$($trip.id)/decisions/$($decision.id)/options/$opt2/vote" @{} $owner.token
    $null = Post-Json "/api/trips/$($trip.id)/decisions/$($decision.id)/options/$opt1/vote" @{} $mate.token

    # 行前清单
    $null = Post-Json "/api/trips/$($trip.id)/checklist" @{ title = "订 11/7 早班高铁票"; assignee = "小B" } $owner.token
    $null = Post-Json "/api/trips/$($trip.id)/checklist" @{ title = "订西湖边民宿（2 间）"; assignee = "小A" } $owner.token
    $item = Post-Json "/api/trips/$($trip.id)/checklist" @{ title = "预约灵隐寺门票" } $mate.token
    # 不要用 $item.title 回填：PS 5.1 读无 charset 的响应会按 Latin-1 解码，再写回就成乱码
    $null = Put-Json "/api/trips/$($trip.id)/checklist/$($item.id)" @{ title = "预约灵隐寺门票"; done = $true } $mate.token

    # 评论
    $null = Post-Json "/api/trips/$($trip.id)/comments" @{ body = "楼外楼要排队，要不要改知味观？" } $mate.token
    $null = Post-Json "/api/trips/$($trip.id)/comments" @{ body = "先按楼外楼订，排不上再换。"; placeId = $placeIds[1] } $owner.token
}

Write-Host ""
Write-Host "登录账号：$DemoEmail / $DemoPassword" -ForegroundColor Cyan
Write-Host "队友账号：$MateEmail / $MatePassword" -ForegroundColor Cyan
Write-Host "行程 id：$($trip.id)" -ForegroundColor Cyan
