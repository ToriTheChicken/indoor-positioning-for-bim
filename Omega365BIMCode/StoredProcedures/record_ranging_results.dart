import 'package:omega365_api/omega365_api.dart';

class ProcRecordRangingResults extends StoredProcedure {
  ProcRecordRangingResults({
    required Omega365HttpClient httpClient,
  }) : super(resourceName: 'astp_BIM_RecordRangingResults', httpClient: httpClient);
}
