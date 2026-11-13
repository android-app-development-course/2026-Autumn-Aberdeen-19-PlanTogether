# PlanTogether API 冒烟测试：覆盖注册/邀请加入/角色权限/协作编辑/投票/清单/动态/导出/级联删除
# 用法：powershell -ExecutionPolicy Bypass -File scripts\api-smoke.ps1
# 需要后端已在 http://127.0.0.1:8080 运行（Windows PowerShell 5.1 / PowerShell 7 均可）

$ErrorActionPreference = "Stop"
$Base = "http://127.0.0.1:8080"
$script:Pass = 0
$script:Fail = 0

Add-Type -AssemblyName System.Net.Http | Out-Null
$script:Client = New-Object System.Net.Http.HttpClient
$script:Client.Timeout = [TimeSpan]::FromSeconds(30)

function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        $Body,
        [string]$Token
    )
    $request = New-Object System.Net.Http.HttpRequestMessage(
        [System.Net.Http.HttpMethod]::new($Method),
        "$Base$Path"
    )
    if ($Token) { $request.Headers.Add("Authorization", "Bearer $Token") }
    if ($null -ne $Body) {
        $json = ConvertTo-Json -InputObject $Body -Depth 10 -Compress
        $request.Content = New-Object System.Net.Http.StringContent(
            $json,
            [System.Text.Encoding]::UTF8,
            "application/json"
        )
    }
    $response = $script:Client.SendAsync($request).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $parsed = $null
    if (-not [string]::IsNullOrWhiteSpace($text)) {
        try { $parsed = $text | ConvertFrom-Json } catch { $parsed = $null }
    }
    return [pscustomobject]@{
        Status = [int]$response.StatusCode
        Json   = $parsed
        Text   = $text
    }
}

function Check {
    param([Parameter(Mandatory)][string]$Name, [bool]$Condition, [string]$Detail = "")
    if ($Condition) {
        $script:Pass++
        Write-Host "  [PASS] $Name" -ForegroundColor Green
    } else {
        $script:Fail++
        Write-Host "  [FAIL] $Name  $Detail" -ForegroundColor Red
    }
}

$suffix = Get-Random -Maximum 999999
$emailA = "organizer$suffix@test.local"
$emailB = "companion$suffix@test.local"
$emailC = "outsider$suffix@test.local"

Write-Host "`n== 账号 ==" -ForegroundColor Cyan
$regA = Invoke-Api POST "/api/auth/register" @{ email = $emailA; password = "password123"; displayName = "组长小A" }
Check "注册组长" ($regA.Status -eq 200 -or $regA.Status -eq 201) "status=$($regA.Status) body=$($regA.Text)"
$tokenA = $regA.Json.token

$regB = Invoke-Api POST "/api/auth/register" @{ email = $emailB; password = "password123"; displayName = "队友小B" }
Check "注册队友" ($regB.Status -eq 200 -or $regB.Status -eq 201) "status=$($regB.Status) body=$($regB.Text)"
$tokenB = $regB.Json.token

$regC = Invoke-Api POST "/api/auth/register" @{ email = $emailC; password = "password123"; displayName = "路人小C" }
Check "注册路人" ($regC.Status -eq 200 -or $regC.Status -eq 201) "status=$($regC.Status) body=$($regC.Text)"
$tokenC = $regC.Json.token

$dup = Invoke-Api POST "/api/auth/register" @{ email = $emailA; password = "password123"; displayName = "重复注册" }
Check "重复邮箱注册被拒绝(400)" ($dup.Status -eq 400) "status=$($dup.Status)"

$me = Invoke-Api GET "/api/auth/me" $null $tokenA
Check "GET /api/auth/me 返回当前用户" ($me.Status -eq 200 -and $me.Json.displayName -eq "组长小A") "status=$($me.Status) body=$($me.Text)"

$meBad = Invoke-Api GET "/api/auth/me" $null "bad-token"
Check "无效 token 访问 /me 返回 401" ($meBad.Status -eq 401) "status=$($meBad.Status)"

Write-Host "`n== 行程与邀请 ==" -ForegroundColor Cyan
$trip = Invoke-Api POST "/api/trips" @{
    title        = "周末去杭州"
    destination  = "杭州"
    startDate    = "2026-11-07"
    endDate      = "2026-11-08"
    groupSize    = 4
    budgetTarget = 2000
} $tokenA
Check "创建行程" ($trip.Status -eq 201) "status=$($trip.Status) body=$($trip.Text)"
$tripId = $trip.Json.id
$inviteCode = $trip.Json.inviteCode
$revNow = Invoke-Api GET "/api/trips/$tripId/revision" $null $tokenA
Check "行程自带 revision 且与 /revision 一致" ($trip.Json.revision -gt 0 -and $trip.Json.revision -eq $revNow.Json.revision) "trip=$($trip.Json.revision) endpoint=$($revNow.Json.revision)"
Check "创建者能拿到邀请码" (-not [string]::IsNullOrWhiteSpace($inviteCode))
Check "创建者角色为 OWNER" ($trip.Json.role -eq "OWNER") "role=$($trip.Json.role)"

$badDate = Invoke-Api POST "/api/trips" @{ title = "日期反了"; startDate = "2026-11-10"; endDate = "2026-11-01" } $tokenA
Check "结束日期早于开始日期被拒绝(400)" ($badDate.Status -eq 400) "status=$($badDate.Status)"

$noAccess = Invoke-Api GET "/api/trips/$tripId" $null $tokenC
Check "非成员访问行程返回 403" ($noAccess.Status -eq 403) "status=$($noAccess.Status) body=$($noAccess.Text)"

$joinBad = Invoke-Api POST "/api/trips/join" @{ inviteCode = "NOTEXIST" } $tokenB
Check "错误邀请码返回 404" ($joinBad.Status -eq 404) "status=$($joinBad.Status)"

$join = Invoke-Api POST "/api/trips/join" @{ inviteCode = $inviteCode.ToLower() } $tokenB
Check "队友用邀请码加入（大小写不敏感）" ($join.Status -eq 200 -and $join.Json.role -eq "EDITOR") "status=$($join.Status) body=$($join.Text)"
Check "邀请码对普通成员隐藏" ($null -eq $join.Json.inviteCode)

$joinAgain = Invoke-Api POST "/api/trips/join" @{ inviteCode = $inviteCode } $tokenB
Check "重复加入是幂等的(200)" ($joinAgain.Status -eq 200) "status=$($joinAgain.Status)"

$joinOwner = Invoke-Api POST "/api/trips/join" @{ inviteCode = $inviteCode } $tokenA
Check "创建者不能加入自己的行程(403)" ($joinOwner.Status -eq 403) "status=$($joinOwner.Status)"

$listB = Invoke-Api GET "/api/trips" $null $tokenB
Check "队友的行程列表包含该行程" ($listB.Status -eq 200 -and @($listB.Json | Where-Object { $_.id -eq $tripId }).Count -eq 1) "body=$($listB.Text)"

$members = Invoke-Api GET "/api/trips/$tripId/members" $null $tokenB
Check "成员列表含创建者与队友" ($members.Status -eq 200 -and @($members.Json).Count -eq 2) "body=$($members.Text)"

$detail = Invoke-Api GET "/api/trips/$tripId" $null $tokenB
Check "memberCount = 2" ($detail.Json.memberCount -eq 2) "memberCount=$($detail.Json.memberCount)"

$forbiddenUpdate = Invoke-Api PUT "/api/trips/$tripId" @{ title = "被篡改的标题" } $tokenB
Check "非创建者不能改行程基本信息(403)" ($forbiddenUpdate.Status -eq 403) "status=$($forbiddenUpdate.Status)"

$forbiddenRotate = Invoke-Api POST "/api/trips/$tripId/invite-code/rotate" $null $tokenB
Check "非创建者不能轮换邀请码(403)" ($forbiddenRotate.Status -eq 403) "status=$($forbiddenRotate.Status)"

Write-Host "`n== 地点协作 ==" -ForegroundColor Cyan
$p1 = Invoke-Api POST "/api/trips/$tripId/places" @{
    name      = "西湖断桥"
    address   = "杭州市西湖区"
    latitude  = 30.2587
    longitude = 120.1489
    date      = "2026-11-07"
    startTime = "09:00"
    endTime   = "11:00"
    budget    = 0
    notes     = "早点去人少"
} $tokenA
Check "组长添加地点" ($p1.Status -eq 201) "status=$($p1.Status) body=$($p1.Text)"
Check "地点记录创建人" ($p1.Json.createdByName -eq "组长小A") "createdByName=$($p1.Json.createdByName)"

$p2 = Invoke-Api POST "/api/trips/$tripId/places" @{
    name      = "楼外楼"
    latitude  = 30.2525
    longitude = 120.1445
    date      = "2026-11-07"
    startTime = "12:00"
    budget    = 400
} $tokenB
Check "队友也能添加地点(EDITOR)" ($p2.Status -eq 201) "status=$($p2.Status) body=$($p2.Text)"
Check "未指定 sortOrder 时自动排到末尾" ($p2.Json.sortOrder -eq 1) "sortOrder=$($p2.Json.sortOrder)"

$places = Invoke-Api GET "/api/trips/$tripId/places" $null $tokenB
Check "地点列表 2 条" (@($places.Json).Count -eq 2) "count=$(@($places.Json).Count)"

$edit = Invoke-Api PUT "/api/trips/$tripId/places/$($p2.Json.id)" @{
    name      = "楼外楼（西湖店）"
    address   = "孤山路30号"
    latitude  = 30.2525
    longitude = 120.1445
    date      = "2026-11-07"
    startTime = "12:00"
    budget    = 450
    notes     = "需要提前订位"
} $tokenB
Check "队友能编辑地点" ($edit.Status -eq 200 -and $edit.Json.budget -eq 450) "status=$($edit.Status) body=$($edit.Text)"

$reorder = Invoke-Api PUT "/api/trips/$tripId/places/reorder" @($p2.Json.id, $p1.Json.id) $tokenB
Check "排序接口生效" ($reorder.Status -eq 200 -and $reorder.Json[0].id -eq $p2.Json.id) "body=$($reorder.Text)"

Write-Host "`n== 评论 ==" -ForegroundColor Cyan
$c1 = Invoke-Api POST "/api/trips/$tripId/comments" @{ body = "楼外楼要排队，改去知味观？"; placeId = $p2.Json.id } $tokenB
Check "队友发表评论（挂在地点上）" ($c1.Status -eq 201 -and $c1.Json.placeId -eq $p2.Json.id) "status=$($c1.Status) body=$($c1.Text)"

$c2 = Invoke-Api POST "/api/trips/$tripId/comments" @{ body = "同意，知味观更稳" } $tokenA
Check "组长发表评论" ($c2.Status -eq 201) "status=$($c2.Status)"

$comments = Invoke-Api GET "/api/trips/$tripId/comments" $null $tokenB
Check "评论列表 2 条" (@($comments.Json).Count -eq 2) "count=$(@($comments.Json).Count)"
Check "作者可删除自己的评论标记" (($comments.Json | Where-Object { $_.id -eq $c1.Json.id }).canDelete -eq $true)

$delByOther = Invoke-Api DELETE "/api/trips/$tripId/comments/$($c2.Json.id)" $null $tokenB
Check "队友不能删组长的评论(403)" ($delByOther.Status -eq 403) "status=$($delByOther.Status)"

$delByOwner = Invoke-Api DELETE "/api/trips/$tripId/comments/$($c1.Json.id)" $null $tokenA
Check "创建者可删成员评论(204)" ($delByOwner.Status -eq 204) "status=$($delByOwner.Status)"

Write-Host "`n== 群决策投票 ==" -ForegroundColor Cyan
$d1 = Invoke-Api POST "/api/trips/$tripId/decisions" @{
    title   = "周六晚上吃什么"
    notes   = "两家都在西湖边"
    options = @("知味观", "新白鹿", "外卖回酒店")
} $tokenA
Check "发起带 3 个选项的决策" ($d1.Status -eq 201 -and @($d1.Json.options).Count -eq 3) "status=$($d1.Status) body=$($d1.Text)"
$decisionId = $d1.Json.id
$opt1 = $d1.Json.options[0].id
$opt2 = $d1.Json.options[1].id

$voteB = Invoke-Api POST "/api/trips/$tripId/decisions/$decisionId/options/$opt1/vote" $null $tokenB
Check "队友投票" (($voteB.Json.options | Where-Object { $_.id -eq $opt1 }).voteCount -eq 1) "body=$($voteB.Text)"

$voteA = Invoke-Api POST "/api/trips/$tripId/decisions/$decisionId/options/$opt2/vote" $null $tokenA
Check "组长投另一个选项" (($voteA.Json.options | Where-Object { $_.id -eq $opt2 }).voteCount -eq 1)
Check "总票数 2" ($voteA.Json.totalVotes -eq 2) "totalVotes=$($voteA.Json.totalVotes)"

$revoteB = Invoke-Api POST "/api/trips/$tripId/decisions/$decisionId/options/$opt2/vote" $null $tokenB
Check "改票后原选项票数归零" (($revoteB.Json.options | Where-Object { $_.id -eq $opt1 }).voteCount -eq 0) "body=$($revoteB.Text)"
Check "改票后新选项 2 票" (($revoteB.Json.options | Where-Object { $_.id -eq $opt2 }).voteCount -eq 2)

$voteAgain = Invoke-Api POST "/api/trips/$tripId/decisions/$decisionId/options/$opt2/vote" $null $tokenB
Check "再点一次取消投票" (($voteAgain.Json.options | Where-Object { $_.id -eq $opt2 }).voteCount -eq 1) "body=$($voteAgain.Text)"

$decide = Invoke-Api PUT "/api/trips/$tripId/decisions/$decisionId/status" @{ status = "DECIDED"; decidedOptionId = $opt2 } $tokenA
Check "定案成功" ($decide.Status -eq 200 -and $decide.Json.status -eq "DECIDED" -and $decide.Json.decidedOptionId -eq $opt2) "body=$($decide.Text)"

$decideBad = Invoke-Api PUT "/api/trips/$tripId/decisions/$decisionId/status" @{ status = "DECIDED" } $tokenA
Check "定案不选选项被拒绝(400)" ($decideBad.Status -eq 400) "status=$($decideBad.Status)"

$addOpt = Invoke-Api POST "/api/trips/$tripId/decisions/$decisionId/options" @{ title = "外卖回酒店" } $tokenB
Check "队友补充选项" ($addOpt.Status -eq 201 -and @($addOpt.Json.options).Count -eq 4) "status=$($addOpt.Status)"

$removeOpt = Invoke-Api DELETE "/api/trips/$tripId/decisions/$decisionId/options/$(@($addOpt.Json.options)[3].id)" $null $tokenB
Check "删除选项" (@($removeOpt.Json.options).Count -eq 3) "count=$(@($removeOpt.Json.options).Count)"

Write-Host "`n== 清单 ==" -ForegroundColor Cyan
$k1 = Invoke-Api POST "/api/trips/$tripId/checklist" @{ title = "订高铁票"; assignee = "小B" } $tokenA
Check "新增清单项" ($k1.Status -eq 201) "status=$($k1.Status) body=$($k1.Text)"
$k2 = Invoke-Api POST "/api/trips/$tripId/checklist" @{ title = "订酒店" } $tokenB
Check "队友也能加清单项" ($k2.Status -eq 201) "body=$($k2.Text)"

$done = Invoke-Api PUT "/api/trips/$tripId/checklist/$($k1.Json.id)" @{ title = "订高铁票"; done = $true; assignee = "小B" } $tokenB
Check "勾选完成" ($done.Json.done -eq $true) "body=$($done.Text)"

Write-Host "`n== 动态 / 导出 ==" -ForegroundColor Cyan
$activity = Invoke-Api GET "/api/trips/$tripId/activity" $null $tokenB
Check "动态列表非空" ($activity.Status -eq 200 -and @($activity.Json).Count -ge 8) "count=$(@($activity.Json).Count)"
Check "动态记录了成员加入" (@($activity.Json | Where-Object { $_.kind -eq "MEMBER_JOINED" }).Count -ge 1)

$export = Invoke-Api GET "/api/trips/$tripId/export" $null $tokenB
Check "导出返回 200" ($export.Status -eq 200) "status=$($export.Status)"
Check "导出内容含标题与章节" ($export.Text -match "周末去杭州" -and $export.Text -match "群决策" -and $export.Text -match "行前清单") "len=$($export.Text.Length)"
Check "导出内容含已定案标记" ($export.Text -match "\[x\] 新白鹿")

Write-Host "`n== 只读成员权限 ==" -ForegroundColor Cyan
$memberB = ($members.Json | Where-Object { $_.email -eq $emailB })
$demote = Invoke-Api PUT "/api/trips/$tripId/members/$($memberB.id)" @{ role = "VIEWER" } $tokenA
Check "创建者把队友降为只读" ($demote.Status -eq 200 -and $demote.Json.role -eq "VIEWER") "body=$($demote.Text)"

$viewerRead = Invoke-Api GET "/api/trips/$tripId/places" $null $tokenB
Check "只读成员仍可查看" ($viewerRead.Status -eq 200) "status=$($viewerRead.Status)"

$viewerWrite = Invoke-Api POST "/api/trips/$tripId/places" @{ name = "只读成员不该能加" } $tokenB
Check "只读成员不能加地点(403)" ($viewerWrite.Status -eq 403) "status=$($viewerWrite.Status)"

$promote = Invoke-Api PUT "/api/trips/$tripId/members/$($memberB.id)" @{ role = "EDITOR" } $tokenA
Check "恢复为可编辑" ($promote.Json.role -eq "EDITOR")

$badRole = Invoke-Api PUT "/api/trips/$tripId/members/$($memberB.id)" @{ role = "GOD" } $tokenA
Check "非法角色被拒绝(400)" ($badRole.Status -eq 400) "status=$($badRole.Status)"

Write-Host "`n== 邀请码轮换 ==" -ForegroundColor Cyan
$rotated = Invoke-Api POST "/api/trips/$tripId/invite-code/rotate" $null $tokenA
Check "创建者轮换邀请码" ($rotated.Status -eq 200 -and $rotated.Json.inviteCode -ne $inviteCode) "body=$($rotated.Text)"
$oldCode = Invoke-Api POST "/api/trips/join" @{ inviteCode = $inviteCode } $tokenC
Check "旧邀请码失效(404)" ($oldCode.Status -eq 404) "status=$($oldCode.Status)"
$newCode = Invoke-Api POST "/api/trips/join" @{ inviteCode = $rotated.Json.inviteCode } $tokenC
Check "新邀请码可加入" ($newCode.Status -eq 200) "status=$($newCode.Status) body=$($newCode.Text)"

$removeMember = Invoke-Api DELETE "/api/trips/$tripId/members/$($memberB.id)" $null $tokenA
Check "创建者移除成员(204)" ($removeMember.Status -eq 204) "status=$($removeMember.Status)"
$afterRemove = Invoke-Api GET "/api/trips/$tripId" $null $tokenB
Check "被移除后无法访问(403)" ($afterRemove.Status -eq 403) "status=$($afterRemove.Status)"

Write-Host "`n== 变更计数 / 乐观锁 / 主动退出 ==" -ForegroundColor Cyan
$rev1 = Invoke-Api GET "/api/trips/$tripId/revision" $null $tokenA
Check "revision 可读且大于 0" ($rev1.Status -eq 200 -and $rev1.Json.revision -gt 0) "body=$($rev1.Text)"
$null = Invoke-Api POST "/api/trips/$tripId/places" @{ name = "并发测试地点"; date = "2026-11-08" } $tokenA
$rev2 = Invoke-Api GET "/api/trips/$tripId/revision" $null $tokenA
Check "新增内容后 revision 变大" ($rev2.Json.revision -gt $rev1.Json.revision) "rev1=$($rev1.Json.revision) rev2=$($rev2.Json.revision)"

$placeList = Invoke-Api GET "/api/trips/$tripId/places" $null $tokenC
$target = @($placeList.Json) | Where-Object { $_.name -eq "并发测试地点" } | Select-Object -First 1
$v0 = $target.version
$updateC = Invoke-Api PUT "/api/trips/$tripId/places/$($target.id)" @{ name = "并发测试地点（C 改过）"; date = "2026-11-08"; version = $v0 } $tokenC
Check "带正确版本号可以保存" ($updateC.Status -eq 200 -and $updateC.Json.version -gt $v0) "status=$($updateC.Status) body=$($updateC.Text)"
$stale = Invoke-Api PUT "/api/trips/$tripId/places/$($target.id)" @{ name = "用旧版本覆盖"; date = "2026-11-08"; version = $v0 } $tokenA
Check "用过期版本号修改返回 409" ($stale.Status -eq 409) "status=$($stale.Status) body=$($stale.Text)"
$fresh = Invoke-Api PUT "/api/trips/$tripId/places/$($target.id)" @{ name = "并发测试地点"; date = "2026-11-08"; version = $updateC.Json.version } $tokenA
Check "按最新版本号重试成功" ($fresh.Status -eq 200) "status=$($fresh.Status) body=$($fresh.Text)"

$leaveOwner = Invoke-Api DELETE "/api/trips/$tripId/members/me" $null $tokenA
Check "创建者不能退出自己的行程(403)" ($leaveOwner.Status -eq 403) "status=$($leaveOwner.Status)"

$leaveC = Invoke-Api DELETE "/api/trips/$tripId/members/me" $null $tokenC
Check "成员可以主动退出(204)" ($leaveC.Status -eq 204) "status=$($leaveC.Status)"
$afterLeave = Invoke-Api GET "/api/trips/$tripId" $null $tokenC
Check "退出后无法再访问(403)" ($afterLeave.Status -eq 403) "status=$($afterLeave.Status)"
$leaveAgain = Invoke-Api DELETE "/api/trips/$tripId/members/me" $null $tokenC
Check "重复退出返回 404" ($leaveAgain.Status -eq 404) "status=$($leaveAgain.Status)"

Write-Host "`n== 错误提示说人话 ==" -ForegroundColor Cyan
# 日期格式写错，以前会 500 + 英文堆栈；现在应该是 400 + 中文提示
$badFormat = Invoke-Api POST "/api/trips" @{ title = "日期格式错"; startDate = "2026/11/07" } $tokenA
Check "日期格式错返回 400（不是 500）" ($badFormat.Status -eq 400) "status=$($badFormat.Status) body=$($badFormat.Text)"
Check "日期格式错的提示是中文" ($badFormat.Json.error -match "日期") "error=$($badFormat.Json.error)"
Check "错误响应不含英文堆栈" ($badFormat.Text -notmatch "Exception|at com\.") "body=$($badFormat.Text)"

$badNumber = Invoke-Api POST "/api/trips" @{ title = "人数不是数字"; groupSize = "很多人" } $tokenA
Check "人数填非数字返回 400" ($badNumber.Status -eq 400) "status=$($badNumber.Status) body=$($badNumber.Text)"

Write-Host "`n== 高德 POI / 地点评分快照 ==" -ForegroundColor Cyan
$anonPoi = Invoke-Api GET "/api/poi/search?q=%E8%A5%BF%E6%B9%96" $null $null
Check "未登录不能搜 POI(401)" ($anonPoi.Status -eq 401) "status=$($anonPoi.Status)"

$poi = Invoke-Api GET "/api/poi/search?q=%E8%A5%BF%E6%B9%96&city=%E6%9D%AD%E5%B7%9E" $null $tokenA
Check "POI 搜索返回 200" ($poi.Status -eq 200) "status=$($poi.Status) body=$($poi.Text)"
# 没配 Key 是正常状态（configured=false + 中文说明），不该报错
Check "POI 带 configured 标志" ($null -ne $poi.Json.configured) "body=$($poi.Text)"
if ($poi.Json.configured -eq $true -and @($poi.Json.items).Count -gt 0) {
    $first = @($poi.Json.items)[0]
    Check "POI 结果带名称" (-not [string]::IsNullOrWhiteSpace($first.name)) "name=$($first.name)"
    Check "POI 结果带经纬度" ($null -ne $first.latitude -and $null -ne $first.longitude) "lat=$($first.latitude) lng=$($first.longitude)"
} else {
    Check "未配 Key 时给出中文说明" (-not [string]::IsNullOrWhiteSpace($poi.Json.message)) "message=$($poi.Json.message)"
}

$emptyQ = Invoke-Api GET "/api/poi/search?q=" $null $tokenA
Check "空关键词不报错" ($emptyQ.Status -eq 200 -or $emptyQ.Status -eq 400) "status=$($emptyQ.Status)"

# 评分快照存下来之后要能原样读回来
$rated = Invoke-Api POST "/api/trips/$tripId/places" @{
    name         = "知味观"
    address      = "仁和路83号"
    rating       = "4.3"
    avgCost      = "85.00"
    openTime     = "09:00-21:00"
} $tokenA
Check "地点可带评分快照保存" ($rated.Status -eq 201 -and $rated.Json.rating -eq "4.3") "status=$($rated.Status) body=$($rated.Text)"
Check "人均与营业时间也能存" ($rated.Json.avgCost -eq "85.00" -and $rated.Json.openTime -eq "09:00-21:00") "body=$($rated.Text)"
Check "评分快照落地后能读回" (@($(Invoke-Api GET "/api/trips/$tripId/places" $null $tokenA).Json | Where-Object { $_.id -eq $rated.Json.id }).rating -eq "4.3")
$null = Invoke-Api DELETE "/api/trips/$tripId/places/$($rated.Json.id)" $null $tokenA

Write-Host "`n== 删除行程级联清理 ==" -ForegroundColor Cyan
$trip2 = Invoke-Api POST "/api/trips" @{ title = "待删除的行程" } $tokenA
$t2 = $trip2.Json.id
$null = Invoke-Api POST "/api/trips/$t2/places" @{ name = "临时地点" } $tokenA
$null = Invoke-Api POST "/api/trips/$t2/links" @{ url = "https://example.com"; title = "示例链接" } $tokenA
$null = Invoke-Api POST "/api/trips/$t2/checklist" @{ title = "临时任务" } $tokenA
$null = Invoke-Api POST "/api/trips/$t2/comments" @{ body = "临时评论" } $tokenA
$d2 = Invoke-Api POST "/api/trips/$t2/decisions" @{ title = "临时决策"; options = @("A", "B") } $tokenA
$null = Invoke-Api POST "/api/trips/$t2/decisions/$($d2.Json.id)/options/$($d2.Json.options[0].id)/vote" $null $tokenA
$del = Invoke-Api DELETE "/api/trips/$t2" $null $tokenA
Check "删除带子数据的行程(204)" ($del.Status -eq 204) "status=$($del.Status) body=$($del.Text)"
$gone = Invoke-Api GET "/api/trips/$t2" $null $tokenA
Check "删除后访问返回 404" ($gone.Status -eq 404) "status=$($gone.Status)"

Write-Host "`n== 清理测试行程 ==" -ForegroundColor Cyan
$delMain = Invoke-Api DELETE "/api/trips/$tripId" $null $tokenA
Check "删除主测试行程(204)" ($delMain.Status -eq 204) "status=$($delMain.Status)"

Write-Host "`n----------------------------------------" -ForegroundColor Cyan
Write-Host ("通过: {0}    失败: {1}" -f $script:Pass, $script:Fail) -ForegroundColor $(if ($script:Fail -eq 0) { "Green" } else { "Red" })
if ($script:Fail -gt 0) { exit 1 } else { exit 0 }
