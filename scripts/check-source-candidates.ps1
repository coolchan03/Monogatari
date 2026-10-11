param([int]$TimeoutSec=8)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$js=Import-Csv (Join-Path $root 'source-audit/js-alternatives.csv')|Where-Object status -eq 'POSSIBLE_JS_ALTERNATIVE'
$kotatsu=Import-Csv (Join-Path $root 'source-audit/kotatsu-matches.csv')|Where-Object status -eq 'POSSIBLE_KOTATSU_MATCH'
$candidates=@($js|ForEach-Object {[pscustomobject]@{name=$_.dartName;url=$_.dartUrl;alternative=$_.jsName;kind='JS'}})
$candidates+=@($kotatsu|ForEach-Object {[pscustomobject]@{name=$_.name;url=$_.site;alternative=$_.parserPaths;kind='Kotatsu'}})
$results=foreach($candidate in $candidates) {
 $status='NETWORK_ERROR';$http='';$finalUrl='';$detail=''
 try {
  $response=Invoke-WebRequest -Uri $candidate.url -UseBasicParsing -MaximumRedirection 4 -TimeoutSec $TimeoutSec
  $http=[int]$response.StatusCode;$status=if($http -eq 200){'SITE_REACHABLE_UNTESTED'}else{'HTTP_ERROR'}
  $finalUrl=$response.BaseResponse.ResponseUri.AbsoluteUri
 } catch {
  $detail=$_.Exception.Message
  if($_.Exception.Response){$http=[int]$_.Exception.Response.StatusCode;$status='HTTP_ERROR'}
 }
 [pscustomobject]@{name=$candidate.name;kind=$candidate.kind;url=$candidate.url;alternative=$candidate.alternative;status=$status;http=$http;finalUrl=$finalUrl;error=$detail}
}
$out=Join-Path $root 'source-audit/candidate-http-check.csv'
$results|Export-Csv -Path $out -NoTypeInformation -Encoding UTF8
$results|Group-Object status|Select-Object Name,Count|Format-Table -AutoSize
Write-Output "HTTP reachability is not parser/search/playback verification. Report: $out"
