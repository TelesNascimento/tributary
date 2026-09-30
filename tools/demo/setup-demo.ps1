param(
    [string]$Root = (Join-Path $env:TEMP 'tributary-demo'),
    [string]$IdeConfigOptions
)

$sandbox = Join-Path $Root 'orders-service'
if (Test-Path $sandbox) { Remove-Item $sandbox -Recurse -Force }
$patches = Join-Path $sandbox '.jazz5\demo\patches'
$main = Join-Path $sandbox 'src\main\java\com\acme\orders'
$test = Join-Path $sandbox 'src\test\java\com\acme\orders'
$resources = Join-Path $sandbox 'src\main\resources'
$idea = Join-Path $sandbox '.idea'
foreach ($dir in $patches, $main, $test, $resources, $idea) { New-Item -ItemType Directory -Force $dir | Out-Null }

function Save([string]$path, [string]$text) { [IO.File]::WriteAllText($path, $text.Replace("`r`n", "`n")) }

Save (Join-Path $main 'OrderService.java') @'
package com.acme.orders;

public final class OrderService {

    public Order place(Order order) {
        validator.check(order);
        return repository.save(order);
    }
}
'@
Save (Join-Path $main 'OrderValidator.java') @'
package com.acme.orders;

final class OrderValidator {

    void check(Order order) {
        if (order == null || order.items().isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one item");
        }
    }
}
'@
Save (Join-Path $main 'TotalCalculator.java') "public final class TotalCalculator {}`n"
Save (Join-Path $main 'OrderRules.java') "final class OrderRules {}`n"
Save (Join-Path $main 'PricingPolicy.java') "final class PricingPolicy {}`n"
Save (Join-Path $test 'TotalCalculatorTest.java') "class TotalCalculatorTest {}`n"
Save (Join-Path $resources 'pricing.properties') "discount.seasonal=false`n"

Save (Join-Path $patches 'OrderService.java.patch') @'
--- a/OrderService.java
+++ b/OrderService.java
@@ -3,5 +3,6 @@
 public final class OrderService {

     public Order place(Order order) {
+        validator.check(order);
         return repository.save(order);
     }
'@
Save (Join-Path $patches 'LegacyMapper.java.patch') @'
--- a/LegacyMapper.java
+++ b/LegacyMapper.java
@@ -1,5 +0,0 @@
-final class LegacyMapper {
-
-    Order map(Object row) {
-        return null;
-    }
'@

Save (Join-Path $idea 'workspace.xml') @'
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="TributaryContexts">
    <option name="activeWorkItem" value="9000101" />
    <option name="contexts">
      <list>
        <Entry>
          <option name="changeSets">
            <list>
              <option value="_DEMOCHANGESET000000001" />
              <option value="_DEMOCHANGESET000000002" />
            </list>
          </option>
          <option name="currentChangeSet" value="_DEMOCHANGESET000000002" />
          <option name="currentComment" value="Add order validation" />
          <option name="lastUsedMillis" value="1790000000000" />
          <option name="summary" value="Order totals are rounded incorrectly" />
          <option name="workItemId" value="9000101" />
          <option name="workspace" value="_DEMOWORKSPACE0000000001" />
        </Entry>
      </list>
    </option>
  </component>
</project>
'@

if ($IdeConfigOptions) {
    New-Item -ItemType Directory -Force $IdeConfigOptions | Out-Null
    $fake = Join-Path $PSScriptRoot 'fake-scm.cmd'
    Save (Join-Path $IdeConfigOptions 'tributary.xml') @"
<application>
  <component name="TributarySettings">
    <option name="activeUri" value="https://rtc.example.com/ccm/" />
    <option name="cliPath" value="$fake" />
    <option name="connections">
      <list>
        <Connection>
          <option name="nickname" value="demo" />
          <option name="uri" value="https://rtc.example.com/ccm/" />
          <option name="userId" value="U000001" />
        </Connection>
      </list>
    </option>
  </component>
</application>
"@
    $trusted = $Root.Replace('\', '/')
    Save (Join-Path $IdeConfigOptions 'trusted-paths.xml') @"
<application>
  <component name="Trusted.Paths.Settings">
    <option name="TRUSTED_PATHS">
      <list>
        <option value="$trusted" />
      </list>
    </option>
  </component>
</application>
"@
}
Write-Host $sandbox
