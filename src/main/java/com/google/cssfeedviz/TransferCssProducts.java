package com.google.cssfeedviz;

import com.google.cssfeedviz.css.ProductsService;
import com.google.cssfeedviz.gcp.BigQueryService;
import com.google.cssfeedviz.utils.AccountInfo;
import com.google.shopping.css.v1.CssProduct;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public class TransferCssProducts {
  private static final String DEFAULT_CONFIG_DIR = "./config";
  private static final String DEFAULT_ACCOUNT_INFO_FILE = "account-info.json";
  private static final String DEFAULT_DATASET_NAME = "css_feedviz";
  private static final String DEFAULT_DATASET_LOCATION = "EU";

  private static final String CONFIG_DIR =
      System.getProperty("feedviz.config.dir", DEFAULT_CONFIG_DIR);
  private static String ACCOUNT_INFO_FILE =
      System.getProperty("feedviz.account.info.file", DEFAULT_ACCOUNT_INFO_FILE);
  private static String DATASET_NAME =
      System.getProperty("feedviz.dataset.name", DEFAULT_DATASET_NAME);
  private static String DATASET_LOCATION =
      System.getProperty("feedviz.dataset.location", DEFAULT_DATASET_LOCATION);

  private static AccountInfo getAccountInfo() throws IOException {
    String accountInfoDomainId = System.getProperty("feedviz.account.info.domain.id");
    String accountInfoDomainIds = System.getProperty("feedviz.account.info.domain.ids");
    String accountInfoGroupId = System.getProperty("feedviz.account.info.group.id");
    String accountInfoMerchantId = System.getProperty("feedviz.account.info.merchant.id");
    List<BigInteger> domainIds = parseDomainIds(accountInfoDomainIds, accountInfoDomainId);
    BigInteger groupId =
        (accountInfoGroupId != null) ? new BigInteger(accountInfoGroupId) : null;
    BigInteger merchantId =
        (accountInfoMerchantId != null) ? new BigInteger(accountInfoMerchantId) : null;
    if (!domainIds.isEmpty() || groupId != null || merchantId != null) {
      return AccountInfo.createWithDomainIds(CONFIG_DIR, merchantId, domainIds, groupId);
    } else {
      return AccountInfo.load(CONFIG_DIR, ACCOUNT_INFO_FILE);
    }
  }

  private static List<BigInteger> parseDomainIds(String domainIds, String domainId) {
    String domainIdsValue = (domainIds != null) ? domainIds : domainId;
    if (domainIdsValue == null || domainIdsValue.isBlank()) {
      return List.of();
    }
    return Arrays.stream(domainIdsValue.split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .map(BigInteger::new)
        .toList();
  }

  public static void main(String[] args) {
    try {
      AccountInfo accountInfo = getAccountInfo();
      if (accountInfo.getDomainIds().isEmpty()) {
        throw new IllegalArgumentException("At least one CSS Domain ID must be provided.");
      }
      BigQueryService bigQueryService = new BigQueryService(accountInfo);
      LocalDateTime transferDate = LocalDateTime.now();

      for (BigInteger domainId : accountInfo.getDomainIds()) {
        AccountInfo domainAccountInfo = accountInfo.forDomainId(domainId);
        ProductsService productsService = ProductsService.create(domainAccountInfo);
        Iterable<CssProduct> cssProducts = productsService.listCssProducts();
        String tableName = BigQueryService.getCssProductsTableName(domainId.toString());
        bigQueryService.streamCssProducts(
            DATASET_NAME, DATASET_LOCATION, tableName, cssProducts, transferDate);
      }
    } catch (Exception e) {
      System.err.println(e.getMessage());
      e.printStackTrace();
    }
  }
}
