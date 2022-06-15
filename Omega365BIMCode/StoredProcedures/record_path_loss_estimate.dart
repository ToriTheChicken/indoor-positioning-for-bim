import 'package:omega365_api/omega365_api.dart';

class ProcRecordPathLossEstimate extends StoredProcedure {
  ProcRecordPathLossEstimate({
    required Omega365HttpClient httpClient,
  }) : super(resourceName: 'astp_BIM_RecordPathLossEstimate', httpClient: httpClient);
}
