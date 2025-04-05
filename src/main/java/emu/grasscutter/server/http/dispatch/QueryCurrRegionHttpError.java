package emu.grasscutter.server.http.dispatch;

import emu.grasscutter.net.proto.QueryCurrRegionHttpRspOuterClass.QueryCurrRegionHttpRsp;
import emu.grasscutter.net.proto.RegionInfoOuterClass;
import emu.grasscutter.net.proto.RetcodeOuterClass;
import emu.grasscutter.net.proto.StopServerInfoOuterClass;

import java.time.Instant;

public class QueryCurrRegionHttpError {
    public static QueryCurrRegionHttpRsp retErrorRsp(String msg, String contentMsg, String url) {

        return QueryCurrRegionHttpRsp.newBuilder()
            .setRetcode(RetcodeOuterClass.Retcode.RET_STOP_SERVER_VALUE)
            .setMsg(msg)
            .setRegionInfo(RegionInfoOuterClass.RegionInfo.newBuilder())
            .setStopServer(
                StopServerInfoOuterClass.StopServerInfo.newBuilder()
                    .setUrl(url)
                    .setStopBeginTime((int) Instant.now().getEpochSecond())
                    .setStopEndTime((int) Instant.now().getEpochSecond() + 60)
                    .setContentMsg(contentMsg)
                    .build())
            .buildPartial();
    }
}
