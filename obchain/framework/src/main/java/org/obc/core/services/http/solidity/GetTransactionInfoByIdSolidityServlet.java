package org.obc.core.services.http.solidity;

import com.google.protobuf.ByteString;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.obc.common.utils.ByteArray;
import org.obc.core.Wallet;
import org.obc.core.services.http.JsonFormat;
import org.obc.core.services.http.PostParams;
import org.obc.core.services.http.RateLimiterServlet;
import org.obc.core.services.http.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.obc.api.GrpcAPI.BytesMessage;
import org.obc.protos.Protocol.TransactionInfo;


@Component
@Slf4j(topic = "API")
public class GetTransactionInfoByIdSolidityServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String input = request.getParameter("value");
      TransactionInfo transInfo = wallet
          .getTransactionInfoById(ByteString.copyFrom(ByteArray.fromHexString(input)));
      if (transInfo == null) {
        response.getWriter().println("{}");
      } else {
        response.getWriter().println(JsonFormat.printToString(transInfo, visible));
      }
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(e.getMessage());
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      PostParams params = PostParams.getPostParams(request);

      BytesMessage.Builder build = BytesMessage.newBuilder();
      JsonFormat.merge(params.getParams(), build, params.isVisible());
      TransactionInfo transInfo = wallet.getTransactionInfoById(build.build().getValue());
      if (transInfo == null) {
        response.getWriter().println("{}");
      } else {
        response.getWriter().println(JsonFormat.printToString(transInfo, params.isVisible()));
      }
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(e.getMessage());
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

}
