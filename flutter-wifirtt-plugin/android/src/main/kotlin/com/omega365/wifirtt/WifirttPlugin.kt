package com.omega365.wifirtt

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer


/** WifirttPlugin */
@TargetApi(Build.VERSION_CODES.S)
class WifirttPlugin: FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var context : Context
  private lateinit var wifiManager : WifiManager
  private lateinit var wifiRttManager : WifiRttManager
  private var blockingResult = mutableListOf<MethodChannel.Result>()
  private val wifiScanReceiver: BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
      if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
        for(result in blockingResult)
          result.success(true)
        blockingResult.clear()
      }
    }
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "wifirtt")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
    wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    wifiRttManager = context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as WifiRttManager
    val intentFilter = IntentFilter()
    intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
    context.registerReceiver(wifiScanReceiver, intentFilter)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    context.unregisterReceiver(wifiScanReceiver)
  }

  override fun onMethodCall(
          @NonNull call: MethodCall,
          @NonNull result: MethodChannel.Result
  ) {
    when (call.method){
      "getPlatformVersion" -> getPlatformVersion(result)
      "getAPs" -> getAPs(result)
      "runRangingRequest" -> runRangingRequest(result,
        call.argument<List<String>?>("positions"))
      "hasWiFiRTTFeature" -> hasWiFiRTTFeature(result)
      "hasWiFiAwareFeature" -> hasWiFiAwareFeature(result)
      "getRSS" -> getRSS(result)
      "scanNetwork" -> scanNetwork(result)
      "trilaterate" -> trilaterate(
              result,
              call.argument<List<List<Double>>>("positions"),
              call.argument<List<Double>>("distances"),
              call.argument<List<Double>>("positionsStdDev"),
              call.argument<List<Double>>("distancesStdDev"),
              call.argument<Int>("weightsType"),
      )
      else -> { result.notImplemented() }
    }
  }

  private fun getPlatformVersion(@NonNull result: MethodChannel.Result){
    result.success("Android ${Build.VERSION.RELEASE}")
  }

  private fun hasWiFiRTTFeature(@NonNull result: MethodChannel.Result){
    result.success(context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT))
  }

  private fun hasWiFiAwareFeature(@NonNull result: MethodChannel.Result){
    result.success(context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE))
  }

  private fun getAPs(@NonNull result: MethodChannel.Result){
    wifiManager.startScan() // this is deprecated, but still needed, as there is no alternative yet.
    result.success(wifiManager.scanResults.filter{ it:ScanResult->it.is80211mcResponder }.map { it:ScanResult->it.BSSID })
  }

  private fun scanNetwork(@NonNull result: MethodChannel.Result){
    blockingResult.add(result)
    wifiManager.startScan()
  }

  private fun getRSS(@NonNull result: MethodChannel.Result) {
    result.success(wifiManager.scanResults.associateBy({ it: ScanResult -> it.BSSID }, { it: ScanResult -> it.level }))
  }

  private fun runRangingRequest(@NonNull result: MethodChannel.Result, accessPoints: List<String>? = null){
    if (wifiRttManager.isAvailable) {
      var aps = wifiManager.scanResults.filter{ it:ScanResult->it.is80211mcResponder }
      if (aps.isEmpty()){
        result.success(null)
        return
      }
      if(accessPoints != null){
        aps = aps.filter { ap->accessPoints.any { it == ap.BSSID }  }
      }
      if(aps.isEmpty()){
        result.success(null)
        return
      }
      val reqBuilder: RangingRequest.Builder = RangingRequest.Builder()
      reqBuilder.addAccessPoints(aps)
      reqBuilder.setRttBurstSize(RangingRequest.getMaxRttBurstSize())
      val req = reqBuilder.build()
      wifiRttManager.startRanging(req, context.mainExecutor, object : RangingResultCallback() {
        override fun onRangingResults(results: List<RangingResult>) {
          val r = results.associateBy({it.macAddress?.toString() ?: "ff:ff:ff:ff:ff:ff"},
            { if(it.status == 0) listOf(it.distanceMm, it.distanceStdDevMm, it.status) else listOf(0,0,it.status)})
          result.success(r)
        }

        override fun onRangingFailure(code: Int) {
          result.success(null)
        }
      })
    } else {
      result.success(null)
    }
  }

  /**
   * Trilaterate function using Levenberg-Marquardt Optimizer with weights.
   */
  private fun trilaterate(
    @NonNull result: MethodChannel.Result,
    @NonNull positions: List<List<Double>>?,
    @NonNull distances: List<Double>?,
    @NonNull positionsStdDev: List<Double>?,
    @NonNull distancesStdDev: List<Double>?,
    @NonNull weightsType: Int?
  ){
    if(positions == null || distances == null){
      result.success(null)
      return
    }
    if(positions.size < 2 || distances.size != positions.size){
      result.success(null)
      return
    }
    val trilateration = TrilaterationFunction(
      positions.map{ it: List<Double> -> it.toDoubleArray()}.toTypedArray(),
      distances.toDoubleArray(),
      positionsStdDev?.toDoubleArray(),
      distancesStdDev?.toDoubleArray(),
    )
    val optimizer = LevenbergMarquardtOptimizer()
    val solver = NonLinearLeastSquaresSolver(trilateration, optimizer, weightsType?:0)
    val solution = solver.solve()
    result.success(solution.point.toArray())
  }
}
