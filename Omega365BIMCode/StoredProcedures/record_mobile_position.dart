import 'package:omega365_api/omega365_api.dart';

class ProcRecordMobilePosition extends StoredProcedure {
  ProcRecordMobilePosition({
    required Omega365HttpClient httpClient,
  }) : super(resourceName: 'astp_BIM_RecordMobilePosition', httpClient: httpClient);
}
