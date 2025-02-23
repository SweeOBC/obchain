package org.obc.core.services.http;

import com.alibaba.fastjson.JSONObject;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.obc.core.Wallet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.obc.protos.Protocol.Transaction;
import org.obc.protos.Protocol.Transaction.Contract.ContractType;
import org.obc.protos.contract.ProposalContract.ProposalCreateContract;


@Component
@Slf4j(topic = "API")
public class ProposalCreateServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {

  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String contract = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);
      boolean visible = Util.getVisiblePost(contract);
      ProposalCreateContract.Builder build = ProposalCreateContract.newBuilder();
      JsonFormat.merge(contract, build, visible);
      Transaction tx = wallet
          .createTransactionCapsule(build.build(), ContractType.ProposalCreateContract)
          .getInstance();
      JSONObject jsonObject = JSONObject.parseObject(contract);
      tx = Util.setTransactionPermissionId(jsonObject, tx);
      response.getWriter().println(Util.printCreateTransaction(tx, visible));
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
}
