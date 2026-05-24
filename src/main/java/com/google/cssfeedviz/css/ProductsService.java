// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cssfeedviz.css;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.retrying.RetrySettings;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cssfeedviz.utils.AccountInfo;
import com.google.cssfeedviz.utils.Authenticator;
import com.google.shopping.css.v1.CssProduct;
import com.google.shopping.css.v1.CssProductsServiceClient;
import com.google.shopping.css.v1.CssProductsServiceClient.ListCssProductsPagedResponse;
import com.google.shopping.css.v1.CssProductsServiceSettings;
import com.google.shopping.css.v1.ListCssProductsRequest;
import com.google.shopping.css.v1.ListCssProductsResponse;
import java.io.IOException;
import java.util.List;
import org.threeten.bp.Duration;

/** A class for handling CSS Products for a given Account */
public class ProductsService implements AutoCloseable {
  private final String DEFAULT_CSS_PRODUCTS_PAGE_SIZE = "0";
  private final String DEFAULT_CSS_PRODUCTS_RPC_TIMEOUT_SECONDS = "300";
  private final String DEFAULT_CSS_PRODUCTS_TOTAL_TIMEOUT_SECONDS = "1800";
  private final int CSS_PRODUCTS_PAGE_SIZE =
      Integer.parseInt(
          System.getProperty("feedviz.css.products.page.size", DEFAULT_CSS_PRODUCTS_PAGE_SIZE));
  private final int CSS_PRODUCTS_RPC_TIMEOUT_SECONDS =
      Integer.parseInt(
          System.getProperty(
              "feedviz.css.products.rpc.timeout.seconds",
              DEFAULT_CSS_PRODUCTS_RPC_TIMEOUT_SECONDS));
  private final int CSS_PRODUCTS_TOTAL_TIMEOUT_SECONDS =
      Integer.parseInt(
          System.getProperty(
              "feedviz.css.products.total.timeout.seconds",
              DEFAULT_CSS_PRODUCTS_TOTAL_TIMEOUT_SECONDS));

  private AccountInfo accountInfo;
  private CssProductsServiceClient cssProductsServiceClient;

  public record CssProductsPage(List<CssProduct> products, String nextPageToken) {}

  private ProductsService() {}

  private String getParent() {
    return String.format("accounts/%d", this.accountInfo.getDomainId());
  }

  private void setAccountInfo(AccountInfo accountInfo) {
    this.accountInfo = accountInfo;
  }

  public static ProductsService create(AccountInfo accountInfo) throws IOException {
    ProductsService productsService = new ProductsService();
    productsService.setAccountInfo(accountInfo);

    GoogleCredentials credential = new Authenticator().authenticate(accountInfo);

    CssProductsServiceSettings.Builder cssProductsServiceSettingsBuilder =
        CssProductsServiceSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credential));
    cssProductsServiceSettingsBuilder
        .listCssProductsSettings()
        .setRetrySettings(productsService.getListCssProductsRetrySettings());
    CssProductsServiceSettings cssProductsServiceSettings =
        cssProductsServiceSettingsBuilder.build();
    CssProductsServiceClient cssProductsServiceClient =
        CssProductsServiceClient.create(cssProductsServiceSettings);
    productsService.setCssProductsServiceClient(cssProductsServiceClient);

    return productsService;
  }

  public void setCssProductsServiceClient(CssProductsServiceClient cssProductsServiceClient) {
    this.cssProductsServiceClient = cssProductsServiceClient;
  }

  private RetrySettings getListCssProductsRetrySettings() {
    return RetrySettings.newBuilder()
        .setInitialRetryDelay(Duration.ofSeconds(1))
        .setRetryDelayMultiplier(2.0)
        .setMaxRetryDelay(Duration.ofSeconds(30))
        .setInitialRpcTimeout(Duration.ofSeconds(CSS_PRODUCTS_RPC_TIMEOUT_SECONDS))
        .setRpcTimeoutMultiplier(1.0)
        .setMaxRpcTimeout(Duration.ofSeconds(CSS_PRODUCTS_RPC_TIMEOUT_SECONDS))
        .setTotalTimeout(Duration.ofSeconds(CSS_PRODUCTS_TOTAL_TIMEOUT_SECONDS))
        .build();
  }

  public Iterable<CssProduct> listCssProducts() {

    String parent = getParent();

    ListCssProductsRequest.Builder requestBuilder =
        ListCssProductsRequest.newBuilder().setParent(parent);
    if (CSS_PRODUCTS_PAGE_SIZE > 0) {
      requestBuilder.setPageSize(CSS_PRODUCTS_PAGE_SIZE);
    }
    ListCssProductsRequest request = requestBuilder.build();

    ListCssProductsPagedResponse response = this.cssProductsServiceClient.listCssProducts(request);
    return response.iterateAll();
  }

  public CssProductsPage listCssProductsPage(String pageToken) {
    String parent = getParent();

    ListCssProductsRequest.Builder requestBuilder =
        ListCssProductsRequest.newBuilder().setParent(parent);
    if (CSS_PRODUCTS_PAGE_SIZE > 0) {
      requestBuilder.setPageSize(CSS_PRODUCTS_PAGE_SIZE);
    }
    if (pageToken != null && !pageToken.isBlank()) {
      requestBuilder.setPageToken(pageToken);
    }

    ListCssProductsResponse response =
        this.cssProductsServiceClient.listCssProductsCallable().call(requestBuilder.build());
    return new CssProductsPage(response.getCssProductsList(), response.getNextPageToken());
  }

  @Override
  public void close() {
    if (this.cssProductsServiceClient != null) {
      this.cssProductsServiceClient.close();
    }
  }
}
