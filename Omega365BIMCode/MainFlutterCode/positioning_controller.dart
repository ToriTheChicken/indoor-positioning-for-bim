import 'dart:convert';
import 'dart:math';

import 'package:geolocator/geolocator.dart';
import 'package:get/get.dart';
import 'package:omega365_api_offline_hive/omega365_api_offline_hive.dart';
import 'package:omega365_bim_viewer/core/models/omega365_offline_hive/checked_out_model.dart';
import 'package:omega365_bim_viewer/core/services/controllers/bim_viewer_controller.dart';
import 'package:omega365_bim_viewer/core/stored_procedures/get_access_points.dart';
import 'package:omega365_bim_viewer/core/stored_procedures/record_mobile_position.dart';
import 'package:omega365_bim_viewer/core/stored_procedures/record_path_loss_estimate.dart';
import 'package:omega365_bim_viewer/core/stored_procedures/record_position_estimate.dart';
import 'package:omega365_bim_viewer/core/stored_procedures/record_ranging_results.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:vector_math/vector_math.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:wifirtt/wifirtt.dart';

typedef AccessPoint = Map<String, dynamic>;
typedef AccessPoints = Map<String, AccessPoint>;
typedef Measurements = Map<String, List<num>>;


class PositioningController extends GetxController {
  static const floorsHeights = [1287, 6567, 11187, 14817, 18447, 21912];

  final BimViewerController bimCtrl = Get.find();
  late double modelLatitude;
  late double modelLongitude;
  bool hasLocationPermission = false;

  late AccessPoints? gAccessPoints;

  int getFloor(num height){
    for(var i = 0; i < floorsHeights.length; i++){
      if(height < floorsHeights[i]){
        return i;
      }
    }
    return -1;
  }

  Future<AccessPoints?> getAccessPoints() async{
    final procParameters = <String, dynamic>{
      'Model_ID': bimCtrl.modelID,
    };
    final accessPoints = await Get.find<ProcGetAccessPoints>()
        .execute(procParameters: procParameters);
    if (accessPoints.hasAbend) {
      await webAlert('Retrieving access points failed, check api/debug/errors');
      return Future.value(null);
    }
    return {
      for (var record in accessPoints.tables[0])
        record['BSSID']: record,
    };
  }

  Future<Measurements?> getRSS() async {
    // RSSI = -(10*n*log(d)+A)
    // d = 10^((A-RSSI)/10*n)
    if (hasLocationPermission || await Permission.location.isGranted ||
      await Permission.location.request().isGranted) {
      hasLocationPermission = true;
      gAccessPoints ??= await getAccessPoints();
      if(gAccessPoints == null){
        return null;
      }
      final rssResponse = await Wifirtt.getRSS();
      if (rssResponse == null) {
        return Future.value(null);
      }
      final rssiValues = Map<String, int>.from(rssResponse)
        ..removeWhere((k, v) => !gAccessPoints!.containsKey(k));
      if(rssiValues.isEmpty){
        return Future.value(null);
      }

      final results = rssiValues.map((key, rssi) {
        final accessPoint = gAccessPoints![key]!;
        final int A = accessPoint['RSSAtOneMeter'] ?? -45;
        final double n = accessPoint['PathLossConstant'] ?? 1.6;
        final distance = pow(10,(A - rssi) /(10 * n));
        return MapEntry(key, [distance, rssi, 0]);
      });
      return results;
    }
    return Future.value(null);
  }

  Future<List<double>?> runWifiRanging(Function which, {bool position3D = true, int recordMode = 0}) async{
    if (hasLocationPermission || await Permission.location.isGranted ||
      await Permission.location.request().isGranted) {
      hasLocationPermission = true;
      final measurements = await which();
      gAccessPoints ??= await getAccessPoints();
      if(gAccessPoints == null){
        return null;
      }
      if(measurements != null){
        final prepared = prepare(measurements, gAccessPoints!, position3D: position3D);
        if(prepared[0].length >= 2){
          var position = await Wifirtt.trilaterate(prepared[0].cast<List<double>>(),
            prepared[1].cast<double>(), prepared[2].cast<double>(),
            prepared[3].cast<double>(), 0);
          if(position != null && position.length == 2){
            final avgFloor = (gAccessPoints!.values
              .where((element) => measurements.containsKey(element['BSSID']))
              .map((e)=>getFloor(e['PositionY'])).reduce((a, b) => a+b)
              /prepared[0].length).round();
            position = [position[0],(floorsHeights[avgFloor]+floorsHeights[avgFloor-1])/2,position[1]];
          }
          if(recordMode > 0){
            await recordPositionEstimate(
              recordMode, position?[0] ?? 0, position?[1] ?? 0, position?[2] ?? 0,
              prepared[0].length);
          }
          return position;
        }
      }
    }
    return Future.value(null);
  }
  // Future<List<double>?> runWifiRssRanging(AccessPoints accessPoints) async{
  //   if (hasLocationPermission || await Permission.location.isGranted ||
  //     await Permission.location.request().isGranted) {
  //     hasLocationPermission = true;
  //     final rssMeasurements = await getRSS(accessPoints);
  //     if(rssMeasurements != null){
  //       final preparedRSS = prepare(rssMeasurements, accessPoints);
  //       if(preparedRSS[0].length >= 2){
  //         final rssPosition = await Wifirtt.trilaterate(preparedRSS[0].cast<List<double>>(),
  //           preparedRSS[1].cast<double>(), preparedRSS[2].cast<double>(),
  //           preparedRSS[3].cast<double>(), 0);
  //         if(rssPosition != null && rssPosition.length == 2){
  //           final avgFloor = (accessPoints.values
  //             .where((element) => rssMeasurements.containsKey(element['BSSID']))
  //             .map((e)=>getFloor(e['PositionY'])).reduce((a, b) => a+b)
  //             /preparedRSS[0].length).round();
  //           return [rssPosition[0],(floorsHeights[avgFloor]+floorsHeights[avgFloor-1])/2,rssPosition[1]];
  //         } else {
  //           return rssPosition;
  //         }
  //       }
  //     }
  //   }
  //   return Future.value(null);
  // }

  Future<Measurements?> getRTT() async {
    if (hasLocationPermission || await Permission.location.isGranted ||
      await Permission.location.request().isGranted) {
      hasLocationPermission = true;
      if (await Wifirtt.hasWiFiRTTFeature()) {
        return Wifirtt.runRangingRequest();
      }
    }
    return Future.value(null);
  }

  List<List<Object>> prepare(Measurements measurements, AccessPoints accessPoints, {bool position3D = true}){
    final positions = [].cast<List<double>>();
    final distances = [].cast<double>();
    final positionsStdDev = [].cast<double>();
    final distancesStdDev = [].cast<double>();
    final successfulMeasurements = measurements..removeWhere((key, value) => value[2] != 0);
    var vPosition3D = position3D;
    if(successfulMeasurements.keys.length < 3){
      vPosition3D = false;
    }
    for (final ap in successfulMeasurements.keys) {
      final measurement = successfulMeasurements[ap];
      final accessPoint = accessPoints[ap];
      if (measurement == null || accessPoint == null) {
        continue;
      }
      if (measurement[2] == 0) {
        // measurement was successful
        if(vPosition3D){
          positions.add([
            accessPoint['PositionX'],accessPoint['PositionY'],accessPoint['PositionZ']
          ].cast<double>());
        } else {
          positions.add([
            accessPoint['PositionX'],accessPoint['PositionZ']
          ].cast<double>());
        }
        distances.add(measurement[0].toDouble());
        positionsStdDev.add(100.0);
        distancesStdDev.add(measurement[1].toDouble());
      }
    }
    return [positions, distances, positionsStdDev, distancesStdDev];
  }

  Future<void> reposition(List<double>? newPosition) async {
    if (newPosition != null){
      if(newPosition.length == 3) {
        await Get.find<WebViewController>().runJavascript(
          """bimControl.views[0].viewport.camera.position.set(
              ${newPosition[0]},${newPosition[1]},${newPosition[2]});
              bimControl.__private.coreViewer.eventHandler.fire('render', {});""");
      } else {
        await webAlert('Reposition camera failed');
      }
    }
  }

  Future<void> repositionXZ(List<double>? newPosition) async {
    if (newPosition != null || newPosition!.length < 2) {
      await Get.find<WebViewController>().runJavascript(
        """bimControl.views[0].viewport.camera.position.setX(${newPosition[0]});
            bimControl.views[0].viewport.camera.position.setZ(${newPosition[1]});
            bimControl.__private.coreViewer.eventHandler.fire('render', {});""");
    } else {
      await webAlert('Reposition camera failed');
    }
  }

  Future<List<double>?> gpsPosition() async {
    if (hasLocationPermission || await Permission.location.isGranted ||
      await Permission.location.request().isGranted) {
      hasLocationPermission = true;
      final position = await Geolocator.getCurrentPosition();
      final distance = Geolocator.distanceBetween(
          modelLatitude, modelLongitude, position.latitude, position.longitude);
      final bearing = Geolocator.bearingBetween(
          position.latitude, position.longitude, modelLatitude, modelLongitude);
      final v = Vector3(0, 0, distance * 1000);
      Matrix4.rotationY(radians(bearing)).transform3(v);
      return [v.x, 0, v.z];
    }
    return Future.value(null);
  }

  Future<List<dynamic>?> runMultipleRttRanging(AccessPoints accessPoints) async{
    if (hasLocationPermission || await Permission.location.isGranted ||
      await Permission.location.request().isGranted) {
      hasLocationPermission = true;
      final mobileDevice = accessPoints['00:00:00:00:00:00'];
      if(mobileDevice == null){
        await webAlert('No mobile record from db');
        return Future.value(null);
      }
      final rangingResults = [];
      final rss = await Wifirtt.getRSS();
      for(var i = 0; i < 5; i++){
        final rtt = await getRTT();
        if(rtt != null){
          for(final bssid in rtt.keys){
            rangingResults.add([
              bssid,
              mobileDevice['PositionX'],
              mobileDevice['PositionY'],
              mobileDevice['PositionZ'],
              rtt[bssid]?[0]??0,
              rtt[bssid]?[1]??0,
              ((rtt[bssid]?[2]??1)==0),
              rss?[bssid]??0
            ]);
          }
        }
      }
      if(rangingResults.isNotEmpty){
        print(rangingResults);
        return rangingResults;
      }
    }
    return Future.value(null);
  }

  Future<void> recordRangingResults(List<dynamic> rangingResults) async {
    final procParametersRecordRangingResults = <String, dynamic>{
      'RangingResults': rangingResults,
    };
    final recordRangingResults =
        await Get.find<ProcRecordRangingResults>()
            .execute(procParameters: procParametersRecordRangingResults);
    if (recordRangingResults.hasAbend) {
      await webAlert('Recording failed, check api/debug/errors');
    } else {
      await webAlert('Recording success');
    }
  }

  // Future<Measurements?> prepareRangingResults(List<dynamic> rangingResults, AccessPoints accessPoints) async{
  //   final filtered = rangingResults.where((element) => element[6] && accessPoints.containsKey(element[0]));
  //   final measurements = {
  //     for(final ap in accessPoints.values)
  //       ap['BSSID'].toString(): [].cast<List<num>>()
  //   };
  //   for(final e in filtered){
  //     measurements[e[0]]!.add([e[4], e[5], e[6]]);
  //   }
  // }

  Future<void> runAll() async {
    if (await Permission.location.isGranted ||
        await Permission.location.request().isGranted) {
      hasLocationPermission = true;
      final positioningController = Get.find<PositioningController>();

      final gpsPosition = await positioningController.gpsPosition();
      await positioningController.recordPositionEstimate(
          1, gpsPosition?[0] ?? 0, gpsPosition?[1] ?? 0, gpsPosition?[2] ?? 0, 0);

      final accessPoints = await getAccessPoints();
      if(accessPoints == null){
        await webAlert('getAccessPoints failed');
        return;
      }
      final rssMeasurements = await positioningController.getRSS();
      if(rssMeasurements != null){
        final preparedRSS = prepare(rssMeasurements, accessPoints);
        final rssPosition = await Wifirtt.trilaterate(preparedRSS[0].cast<List<double>>(),
          preparedRSS[1].cast<double>(), preparedRSS[2].cast<double>(),
          preparedRSS[3].cast<double>(), 1);
        if(rssPosition != null && rssPosition.length == 2){
          final avgFloor = (accessPoints.values
            .where((element) => rssMeasurements.containsKey(element['BSSID']))
            .map((e)=>getFloor(e['PositionY'])).reduce((a, b) => a+b)
            /preparedRSS[0].length).round();
          await positioningController.recordPositionEstimate(
            2, rssPosition[0], (floorsHeights[avgFloor]+floorsHeights[avgFloor-1])/2, rssPosition[1],
            preparedRSS[0].length);
        } else {
          await positioningController.recordPositionEstimate(
            2, rssPosition?[0] ?? 0, rssPosition?[1] ?? 0, rssPosition?[2] ?? 0,
            preparedRSS[0].length);
        }
        await webAlert('RSS succeeded');
      } else {
        await webAlert('RSS failed');
      }

      final rttMeasurements = await positioningController.getRTT();
      if(rttMeasurements != null){
        final preparedRTT = prepare(rttMeasurements, accessPoints);
        if(preparedRTT[0].length < 2){
          await webAlert('RTT failed');
        } else {
          await webAlert('RTT succeeded');
        }
        final rttPositionW0 = await Wifirtt.trilaterate(preparedRTT[0].cast<List<double>>(),
          preparedRTT[1].cast<double>(), preparedRTT[2].cast<double>(),
          preparedRTT[3].cast<double>(), 0);
        final rttPositionW1 = await Wifirtt.trilaterate(preparedRTT[0].cast<List<double>>(),
          preparedRTT[1].cast<double>(), preparedRTT[2].cast<double>(),
          preparedRTT[3].cast<double>(), 1);
        final rttPositionW2 = await Wifirtt.trilaterate(preparedRTT[0].cast<List<double>>(),
          preparedRTT[1].cast<double>(), preparedRTT[2].cast<double>(),
          preparedRTT[3].cast<double>(), 2);

        if(preparedRTT[0].length < 3){
          final avgFloor = (accessPoints.values
            .where((element) => rttMeasurements.containsKey(element['BSSID']))
            .map((e)=>getFloor(e['PositionY'])).reduce((a, b) => a+b)
            /preparedRTT[0].length).round();
        await positioningController.recordPositionEstimate(
            3, rttPositionW0?[0] ?? 0, (floorsHeights[avgFloor]+floorsHeights[avgFloor-1])/2, rttPositionW0?[1] ?? 0,
            preparedRTT[0].length);
        await positioningController.recordPositionEstimate(
            4, rttPositionW1?[0] ?? 0, (floorsHeights[avgFloor]+floorsHeights[avgFloor-1])/2, rttPositionW1?[1] ?? 0,
            preparedRTT[0].length);
        await positioningController.recordPositionEstimate(
            5, rttPositionW2?[0] ?? 0, (floorsHeights[avgFloor]+floorsHeights[avgFloor-1])/2, rttPositionW2?[1] ?? 0,
            preparedRTT[0].length);
        } else {
        await positioningController.recordPositionEstimate(
            3, rttPositionW0?[0] ?? 0, rttPositionW0?[1] ?? 0, rttPositionW0?[2] ?? 0,
            preparedRTT[0].length);
        await positioningController.recordPositionEstimate(
            4, rttPositionW1?[0] ?? 0, rttPositionW1?[1] ?? 0, rttPositionW1?[2] ?? 0,
            preparedRTT[0].length);
        await positioningController.recordPositionEstimate(
            5, rttPositionW2?[0] ?? 0, rttPositionW2?[1] ?? 0, rttPositionW2?[2] ?? 0,
            preparedRTT[0].length);
        }
      } else {
        await webAlert('RTT failed');
      }
    }
  }

  Future<bool?> scanNetwork() async => Wifirtt.scanNetwork();

  Future<bool> recordPositionEstimate(
      int method, double x, double y, double z, int successfulMeasurements) async {
    final procParameters = <String, dynamic>{
      'method': method,
      'x': x,
      'y': y,
      'z': z,
      'referencePoints': successfulMeasurements,
      'model': bimCtrl.modelID
    };
    final response = await Get.find<ProcRecordPositionEstimate>()
        .execute(procParameters: procParameters);
    if (response.hasAbend) {
      await webAlert('Recording failed, check api/debug/errors');
      return Future.value(false);
    }
    return Future.value(true);
  }

  Future<void> webAlert(String message) async => 
    Get.find<WebViewController>().runJavascript("alert('$message');");

  Future<bool> recordPathLossConstantEstimate(AccessPoints accessPoints) async {
    if (hasLocationPermission ||
        await Permission.location.request().isGranted) {
      hasLocationPermission = true;
      final rssResponse = await Wifirtt.getRSS();
      if (rssResponse == null) {
        await webAlert('rss failed');
        return Future.value(false);
      }
      final mobileDevice = accessPoints['00:00:00:00:00:00'];
      if(mobileDevice == null){
        await webAlert('No mobile record from db');
        return Future.value(false);
      }
      final mobilePosition = [mobileDevice['PositionX'],mobileDevice['PositionY'],mobileDevice['PositionZ']];
      final rssiValues = Map<String, int>.from(rssResponse)
        ..removeWhere((k, v) => !accessPoints.containsKey(k));

      final pathLossConstants = List<List<dynamic>>.empty(growable: true);
      for (final BSSID in accessPoints.keys) {
        final ap = accessPoints[BSSID];
        if (ap == null || ap == mobileDevice) {
          await webAlert('Missing BSSID $ap');
          continue;
        }
        final rssi = rssiValues[BSSID];
        final int? A = ap['RSSAtOneMeter'];
        int? d;
        if(ap.containsKey('PositionX') && ap.containsKey('PositionY') && ap.containsKey('PositionZ')){
          d = sqrt(pow(ap['PositionX']-mobilePosition[0],2)
                  +pow(ap['PositionY']-mobilePosition[1],2)
                  +pow(ap['PositionZ']-mobilePosition[2],2)).round();
        }
        if (rssi == null || A == null || d == null) {
          continue;
        }
        final n = (A-rssi)/(10*log(d/1000)/ln10);
        await webAlert('BSSID: ${ap['BSSID']} n: $n, rssi: $rssi, A: $A, d: $d');
        pathLossConstants.add([d, n, ap['BSSID']]);
      }
      if (pathLossConstants.isEmpty) {
        await webAlert('No estimates recorded');
        return Future.value(false);
      }
      final procParametersRecordEstimate = <String, dynamic>{
        'Estimates': pathLossConstants,
      };
      final recordEstimateResponse =
          await Get.find<ProcRecordPathLossEstimate>()
              .execute(procParameters: procParametersRecordEstimate);
      if (recordEstimateResponse.hasAbend) {
        await webAlert('Recording failed, check api/debug/errors');
        return Future.value(false);
      }
      return Future.value(true);
    }
    return Future.value(false);
  }

  Future<bool> repositionMobile() async {
    final cameraPositionJson = await Get.find<WebViewController>()
        .runJavascriptReturningResult(
            'bimControl.views[0].viewport.camera.position');
    if (cameraPositionJson.isNotEmpty) {
      final obj = jsonDecode(cameraPositionJson);
      final procParametersRecordMobilePosition = <String, dynamic>{
        'x': obj['x'],
        'y': obj['y'],
        'z': obj['z']
      };
      final resp = await Get.find<ProcRecordMobilePosition>()
        .execute(procParameters: procParametersRecordMobilePosition);
      if (!resp.hasAbend) {
        await webAlert('Reposition success');
        return Future.value(true);
      } else {
        await webAlert('Recording failed, check api/debug/errors');
        return Future.value(false);
      }
    }
    await webAlert('Reposition failed');
    return Future.value(false);
  }

  PositioningController() {
    modelLatitude = 0;
    modelLongitude = 0;
    getAccessPoints().then((value) => gAccessPoints = value);
    Get.find<Omega365OfflineHiveDataResource<CheckedOutModel>>()
      .retrieveRecordPage(Omega365OfflineHiveRetrieveRecordPageOptions(
        fields: CheckedOutModel.allFields,
        updatePagedApiList: false,
        whereObject: FilterExpression(
          filterOperator: FilterOperator.equals,
          filterValueType: FilterValueType.number,
          value: bimCtrl.modelID,
          column: 'ID',
        ),
      ))
      .then((val) => {
        if (val.hasAbend){
          webAlert('Retrieving model data, check api/debug/errors')
        } else if (val.recordPage != null && val.recordPage!.isNotEmpty) {
            if (val.recordPage![0].latitude == null ||
                val.recordPage![0].longitude == null) {
              //todo(th) handle error
              modelLatitude = 0, modelLongitude = 0
            } else {
              modelLatitude = val.recordPage![0].latitude!.toDouble(),
              modelLongitude = val.recordPage![0].longitude!.toDouble()
            }
          }
      });
  }
}
