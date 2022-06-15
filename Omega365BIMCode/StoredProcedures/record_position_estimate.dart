import 'package:omega365_api/omega365_api.dart';

class ProcRecordPositionEstimate extends StoredProcedure {
  ProcRecordPositionEstimate({
    required Omega365HttpClient httpClient,
  }) : super(resourceName: 'astp_BIM_RecordPositionEstimate', httpClient: httpClient);
}
