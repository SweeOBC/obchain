/*
 * java-obc is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-obc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obc.core.actuator;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import static org.obc.core.actuator.ActuatorConstant.CONTRACT_NOT_EXIST;
import static org.obc.core.actuator.ActuatorConstant.STORE_NOT_EXIST;
import static org.obc.core.actuator.ActuatorConstant.TX_RESULT_NULL;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

import org.obc.common.utils.Commons;
import org.obc.common.utils.DecodeUtil;
import org.obc.core.capsule.AccountCapsule;
import org.obc.core.capsule.MarketOrderCapsule;
import org.obc.core.capsule.MarketOrderIdListCapsule;
import org.obc.core.capsule.TransactionResultCapsule;
import org.obc.core.capsule.utils.MarketUtils;
import org.obc.core.exception.BalanceInsufficientException;
import org.obc.core.exception.ContractExeException;
import org.obc.core.exception.ContractValidateException;
import org.obc.core.exception.ItemNotFoundException;
import org.obc.core.store.AccountStore;
import org.obc.core.store.AssetIssueStore;
import org.obc.core.store.DynamicPropertiesStore;
import org.obc.core.store.MarketAccountStore;
import org.obc.core.store.MarketOrderStore;
import org.obc.core.store.MarketPairPriceToOrderStore;
import org.obc.core.store.MarketPairToPriceStore;
import org.obc.protos.Protocol.MarketOrder.State;
import org.obc.protos.Protocol.Transaction.Contract.ContractType;
import org.obc.protos.Protocol.Transaction.Result.code;
import org.obc.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;
import org.obc.protos.contract.MarketContract.MarketCancelOrderContract;

@Slf4j(topic = "actuator")
public class MarketCancelOrderActuator extends AbstractActuator {

  private AccountStore accountStore;
  private DynamicPropertiesStore dynamicStore;
  private AssetIssueStore assetIssueStore;

  private MarketAccountStore marketAccountStore;
  private MarketOrderStore orderStore;
  private MarketPairToPriceStore pairToPriceStore;
  private MarketPairPriceToOrderStore pairPriceToOrderStore;

  public MarketCancelOrderActuator() {
    super(ContractType.MarketCancelOrderContract, MarketCancelOrderContract.class);
  }

  private void initStores() {
    accountStore = chainBaseManager.getAccountStore();
    dynamicStore = chainBaseManager.getDynamicPropertiesStore();
    assetIssueStore = chainBaseManager.getAssetIssueStore();

    marketAccountStore = chainBaseManager.getMarketAccountStore();
    orderStore = chainBaseManager.getMarketOrderStore();
    pairToPriceStore = chainBaseManager.getMarketPairToPriceStore();
    pairPriceToOrderStore = chainBaseManager.getMarketPairPriceToOrderStore();
  }

  @Override
  public boolean execute(Object object) throws ContractExeException {

    initStores();

    TransactionResultCapsule ret = (TransactionResultCapsule) object;
    if (Objects.isNull(ret)) {
      throw new RuntimeException(TX_RESULT_NULL);
    }
    long fee = calcFee();

    try {
      final MarketCancelOrderContract contract = this.any
          .unpack(MarketCancelOrderContract.class);

      AccountCapsule accountCapsule = accountStore
          .get(contract.getOwnerAddress().toByteArray());

      byte[] orderId = contract.getOrderId().toByteArray();
      MarketOrderCapsule orderCapsule = orderStore.get(orderId);

      // fee
      accountCapsule.setBalance(accountCapsule.getBalance() - fee);
      Commons.adjustBalance(accountStore, accountStore.getBlackhole().createDbKey(), fee);

      // 1. return balance and token
      MarketUtils.returnSellTokenRemain(orderCapsule, accountCapsule, dynamicStore, assetIssueStore);

      MarketUtils.updateOrderState(orderCapsule, State.CANCELED, marketAccountStore);
      accountStore.put(orderCapsule.getOwnerAddress().toByteArray(), accountCapsule);
      orderStore.put(orderCapsule.getID().toByteArray(), orderCapsule);

      // 2. clear orderList
      byte[] pairPriceKey = MarketUtils.createPairPriceKey(
          orderCapsule.getSellTokenId(),
          orderCapsule.getBuyTokenId(),
          orderCapsule.getSellTokenQuantity(),
          orderCapsule.getBuyTokenQuantity()
      );
      MarketOrderIdListCapsule orderIdListCapsule = pairPriceToOrderStore.get(pairPriceKey);

      // delete order
      orderIdListCapsule.removeOrder(orderCapsule, orderStore, pairPriceKey, pairPriceToOrderStore);

      if (orderIdListCapsule.isOrderEmpty()) {
        // if orderList is empty, delete
        pairPriceToOrderStore.delete(pairPriceKey);

        // 3. modify priceList
        // decrease price number
        // if empty, delete token pair
        byte[] makerPair = MarketUtils
            .createPairKey(orderCapsule.getSellTokenId(), orderCapsule.getBuyTokenId());
        long remainCount = pairToPriceStore.getPriceNum(makerPair) - 1;
        if (remainCount == 0) {
          pairToPriceStore.delete(makerPair);
        } else {
          pairToPriceStore.setPriceNum(makerPair, remainCount);
        }
      }

      ret.setStatus(fee, code.SUCESS);
    } catch (ItemNotFoundException
        | InvalidProtocolBufferException
        | BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.any == null) {
      throw new ContractValidateException(CONTRACT_NOT_EXIST);
    }
    if (chainBaseManager == null) {
      throw new ContractValidateException(STORE_NOT_EXIST);
    }

    initStores();

    if (!this.any.is(MarketCancelOrderContract.class)) {
      throw new ContractValidateException(
          "contract type error,expected type [MarketCancelOrderContract],real type[" + any
              .getClass() + "]");
    }

    if (!dynamicStore.supportAllowMarketTransaction()) {
      throw new ContractValidateException("Not support Market Transaction, need to be opened by"
          + " the committee");
    }

    final MarketCancelOrderContract contract;
    try {
      contract =
          this.any.unpack(MarketCancelOrderContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    // Parameters check
    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    ByteString orderId = contract.getOrderId();

    if (!DecodeUtil.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    // Whether the account exist
    AccountCapsule ownerAccount = accountStore.get(ownerAddress);
    if (ownerAccount == null) {
      throw new ContractValidateException("Account does not exist!");
    }

    // Whether the order exist
    MarketOrderCapsule marketOrderCapsule;
    try {
      marketOrderCapsule = orderStore.get(orderId.toByteArray());
    } catch (ItemNotFoundException ex) {
      throw new ContractValidateException(
          "orderId not exists");
    }

    if (!marketOrderCapsule.isActive()) {
      throw new ContractValidateException("Order is not active!");
    }

    if (!marketOrderCapsule.getOwnerAddress().equals(ownerAccount.getAddress())) {
      throw new ContractValidateException("Order does not belong to the account!");
    }

    // Whether the balance is enough
    long fee = calcFee();
    if (ownerAccount.getBalance() < fee) {
      throw new ContractValidateException("No enough balance !");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return any.unpack(AssetIssueContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dynamicStore.getMarketCancelFee();
  }

}
