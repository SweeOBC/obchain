package org.obc.core.services.interfaceOnSolidity.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.obc.core.services.http.ScanShieldedTRC20NotesByIvkServlet;
import org.obc.core.services.interfaceOnSolidity.WalletOnSolidity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
@Slf4j(topic = "API")
public class ScanShieldedTRC20NotesByIvkOnSolidityServlet extends
    ScanShieldedTRC20NotesByIvkServlet {


  @Autowired
  private WalletOnSolidity walletOnSolidity;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    walletOnSolidity.futureGet(() -> super.doGet(request, response));
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    walletOnSolidity.futureGet(() -> super.doPost(request, response));
  }
}
