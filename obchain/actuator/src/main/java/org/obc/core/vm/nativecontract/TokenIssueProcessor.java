package org.obc.core.vm.nativecontract;

import static org.obc.core.vm.nativecontract.ContractProcessorConstant.*;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

import org.obc.common.utils.ByteArray;
import org.obc.common.utils.DecodeUtil;
import org.obc.core.capsule.AccountCapsule;
import org.obc.core.capsule.AssetIssueCapsule;
import org.obc.core.exception.ContractValidateException;
import org.obc.core.utils.TransactionUtil;
import org.obc.core.vm.nativecontract.param.TokenIssueParam;
import org.obc.core.vm.repository.Repository;

@Slf4j(topic = "Processor")
public class TokenIssueProcessor {

  public void execute(Object contract, Repository repository) {
    TokenIssueParam tokenIssueParam = (TokenIssueParam) contract;
    long tokenIdNum = repository.getTokenIdNum();
    tokenIdNum++;
    repository.saveTokenIdNum(tokenIdNum);
    AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(tokenIssueParam.getOwnerAddress(),
        Long.toString(tokenIdNum), ByteArray.toStr(tokenIssueParam.getName()),
        ByteArray.toStr(tokenIssueParam.getAbbr()), tokenIssueParam.getTotalSupply(),
        tokenIssueParam.getPrecision());
    repository.putAssetIssueValue(assetIssueCapsule.createDbV2Key(), assetIssueCapsule);
    AccountCapsule accountCapsule = repository.getAccount(tokenIssueParam.getOwnerAddress());
    accountCapsule.setAssetIssuedName(assetIssueCapsule.getName().toByteArray());
    accountCapsule.setAssetIssuedID(ByteArray.fromString(assetIssueCapsule.getId()));
    accountCapsule
        .addAssetV2(assetIssueCapsule.createDbV2Key(), tokenIssueParam.getTotalSupply());
    accountCapsule.setInstance(accountCapsule.getInstance().toBuilder().build());
    // spend 1024obc for assetissue, send to blackhole address
    AccountCapsule bhAccountCapsule = repository.getAccount(repository.getBlackHoleAddress());
    bhAccountCapsule.setBalance(Math.addExact(bhAccountCapsule.getBalance(),
        repository.getDynamicPropertiesStore().getAssetIssueFee()));
    accountCapsule.setBalance(Math.subtractExact(accountCapsule.getBalance(),
        repository.getDynamicPropertiesStore().getAssetIssueFee()));
    repository.putAccountValue(tokenIssueParam.getOwnerAddress(), accountCapsule);
    repository.putAccountValue(bhAccountCapsule.getAddress().toByteArray(), bhAccountCapsule);
  }

  public void validate(Object contract, Repository repository) throws ContractValidateException {
    if (Objects.isNull(contract)) {
      throw new ContractValidateException(CONTRACT_NULL);
    }
    if (repository == null) {
      throw new ContractValidateException(STORE_NOT_EXIST);
    }
    if (!(contract instanceof TokenIssueParam)) {
      throw new ContractValidateException(
          "contract type error,expected type [TokenIssuedContract],real type[" + contract
              .getClass() + "]");
    }
    TokenIssueParam tokenIssueParam = (TokenIssueParam) contract;
    if (!DecodeUtil.addressValid(tokenIssueParam.getOwnerAddress())) {
      throw new ContractValidateException("Invalid ownerAddress");
    }
    if (!TransactionUtil.validAssetName(tokenIssueParam.getName())) {
      throw new ContractValidateException("Invalid assetName");
    }
    if ((obc.equalsIgnoreCase(ByteArray.toStr(tokenIssueParam.getName())))) {
      throw new ContractValidateException("assetName can't be obc or null");
    }
    if (tokenIssueParam.getPrecision() < 0
        || tokenIssueParam.getPrecision() > TOKEN_ISSUE_PRECISION) {
      throw new ContractValidateException("precision cannot exceed 6");
    }
    if (Objects.nonNull(tokenIssueParam.getAbbr())
        && !TransactionUtil.validAssetName(tokenIssueParam.getAbbr())) {
      throw new ContractValidateException("Invalid abbreviation for token");
    }
    if (tokenIssueParam.getTotalSupply() <= 0) {
      throw new ContractValidateException("TotalSupply must greater than 0!");
    }
    AccountCapsule accountCapsule = repository.getAccount(tokenIssueParam.getOwnerAddress());
    if (accountCapsule == null) {
      throw new ContractValidateException("Account not exists");
    }
    if (!accountCapsule.getAssetIssuedName().isEmpty()) {
      throw new ContractValidateException("An account can only issue one asset");
    }
    if (accountCapsule.getBalance() < repository.getDynamicPropertiesStore().getAssetIssueFee()) {
      throw new ContractValidateException("No enough balance for fee!");
    }
  }
}
