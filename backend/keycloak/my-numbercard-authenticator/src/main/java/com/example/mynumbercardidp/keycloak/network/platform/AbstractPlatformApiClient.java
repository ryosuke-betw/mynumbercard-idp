package com.example.mynumbercardidp.keycloak.network.platform;

import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.keycloak.models.UserModel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.http.HttpTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.ws.rs.core.MultivaluedMap;

/**
 * 個人番号カードの公的個人認証部分を受け付けるプラットフォームと通信する処理を定義すべき実装を表しています。
 *
 * このクラスを継承したサブクラスは、インスタンス生成元で{@link #init(MultivaluedMap<String, String>)}を実行し、プラットフォームへ送信するパラメータを受け取る必要があります。
 */
public abstract class AbstractPlatformApiClient implements PlatformApiClientImpl {

    /** プラットフォームと通信するときのリクエスト設定 */
    protected RequestConfig httpRequestConfig;
    protected Timeout httpConnectTimeout;
    protected Timeout httpRequestTimeout;
    /** プラットフォームに送信するHTTP Body構造 */
    protected HttpEntity httpEntity;
    /** プラットフォームに送信するコンテンツタイプ */
    protected ContentType httpRequestContentType;
    /** プラットフォームのAPIルートURI */
    protected URI apiRootUri;
    /** プラットフォームに送信するHTTP Bodyの文字セット */
    protected Charset defaultCharset;
    /** ユーザーリクエストのデータ構造 */
    protected UserRequestModel userRequest;
    /** プラットフォームリクエストのデータ構造 */
    protected PlatformRequestModel platformRequest;
    /** プラットフォームレスポンスのデータ構造 */
    protected PlatformResponseModel platformResponse;
    /** プラットフォームへ送るIDP識別送信者符号 */
    protected String platformRequestSender;

    {
        defaultCharset = Charset.forName("UTF-8");
    }

    @Nullable 
    protected RequestConfig getHttpRequestConfig() {
        return httpRequestConfig;
    }

    protected void setHttpRequestConfig(RequestConfig config) {
        httpRequestConfig = config;
    }

    @Nullable 
    protected Timeout getHttpConnectTimeout() {
        return httpConnectTimeout;
    }

    protected void setHttpConnectTimeout(Timeout timeout) {
        httpConnectTimeout = timeout;
    }

    protected void setHttpConnectTimeout(long timeout, TimeUnit timeUnit) {
        httpConnectTimeout = Timeout.of(timeout, timeUnit);
    }

    @Nullable
    protected Timeout getHttpRequestTimeout() {
        return httpRequestTimeout;
    }

    protected void setHttpRequestTimeout(Timeout timeout) {
        httpRequestTimeout = timeout;
    }

    protected void setHttpRequestTimeout(long timeout, TimeUnit timeUnit) {
        httpRequestTimeout = Timeout.of(timeout, timeUnit);
    }

    @Nullable 
    protected HttpEntity getHttpEntity() {
        return httpEntity;
    }

    protected void setHttpEntity(HttpEntity httpEntity) {
       this.httpEntity = httpEntity;
    }

    protected ContentType getHttpRequestContentType() {
        return httpRequestContentType;
    }

    protected void setHttpRequestContentType(ContentType contentType) {
       httpRequestContentType = contentType;
    }

    protected URI getApiRootUri() {
        return apiRootUri;
    }

    protected void setApiRootUri(URI uri) {
       apiRootUri = uri;
    }

    protected Charset getDefaultCharset() {
        return defaultCharset;
    }

    protected void setDefaultCharset(Charset charset) {
       defaultCharset = charset;
    }

    @Override
    public UserRequestModel getUserRequest() {
        return userRequest;
    }

    protected void setUserRequest(UserRequestModel request) {
        userRequest = request;
    }

    @Override
    public PlatformRequestModel getPlatformRequest() {
        return platformRequest;
    }

    protected void setPlatformRequest(PlatformRequestModel request) {
        platformRequest = request;
    }

    @Nullable
    @Override
    public PlatformResponseModel getPlatformResponse() {
        return platformResponse;
    }

    protected void setPlatformResponse(PlatformResponseModel response) {
       platformResponse = response;
    }

    protected String getPlatformRequestSender() {
        return platformRequestSender;
    }

    protected void setPlatformRequestSender(String idpSender) {
       platformRequestSender = idpSender;
    }

    /**
     * プラットフォームのAPIへデータを送信します。
     *
     * @param apiUri プラットフォームのAPI URI
     * @param headers HTTP リクエストのヘッダー
     * @param entity HTTP リクエストのボディ
     * @return プラットフォームのレスポンス
     */
    protected void post(URI apiUri, Header[] headers, HttpEntity entity) {
        HttpPost httpPost = new HttpPost(apiUri);
        httpPost.setHeaders(headers);
        httpPost.setEntity(entity);

        CloseableHttpClient httpClient = HttpClientBuilder.create()
            .setDefaultRequestConfig(buildRequestConfig())
            .build();
        try (CloseableHttpResponse httpResponse = httpClient.execute(httpPost)) {
            platformResponse = toPlatformResponse(httpResponse);
        } catch (HttpTimeoutException e) {
            String message = "Connect timeout. Platform URL: " + apiUri.toString();
            throw new IllegalArgumentException(message, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * プラットフォームのAPIへデータを送信します。
     *
     * プラットフォームのAPIへ送信するリクエストヘッダーはMapから作成します。
     *
     * @param apiUri プラットフォームのAPI URI
     * @param headersMap HTTP リクエストのヘッダー
     * @param entity HTTP リクエストのボディ
     * @return プラットフォームのレスポンス
     */
    protected void post(URI apiUri, MultivaluedMap<String, Object> headersMap, HttpEntity entity) {
        ArrayList<Header> headers = new ArrayList<>();
        headersMap.forEach((key, value) -> headers.add(new BasicHeader(key, value)));
        post(apiUri, headers.toArray(new Header[headers.size()]), entity);
    }

    private boolean isNonNullHttpConnectTimeout() {
        return Objects.nonNull(httpConnectTimeout); 
    }

    private boolean isNonNullHttpRequestTimeout() {
        return Objects.nonNull(httpRequestTimeout); 
    }

    private boolean isNullHttpTimeout() {
        return Objects.isNull(httpConnectTimeout) &&
               Objects.isNull(httpRequestTimeout); 
    }

    /**
     * コネクションタイムアウトの設定を含むHTTPリクエスト設定の構成を作成します。
     *
     * @return HTTPリクエスト設定の構成
     */
    protected RequestConfig buildRequestConfig() {
        if (isNullHttpTimeout()) {
            return RequestConfig.DEFAULT;
        }

        RequestConfig.Builder config = RequestConfig.custom();
        if (isNonNullHttpConnectTimeout()) {
            config.setConnectTimeout(httpConnectTimeout);
        }
        if (isNonNullHttpRequestTimeout()) {
            config.setResponseTimeout(httpRequestTimeout);
        }
        return config.build();
    }

    /**
     * 文字列からHTTPリクエストのボディを作成します。
     *
     * @param s HTTPリクエストボディの文字列
     * @return HTTPリクエストボディ
     */
    protected final HttpEntity createHttpEntity(String s) {
        return createHttpEntity(s.getBytes(defaultCharset), httpRequestContentType);
    }

    /**
     * 文字セットを指定し、文字列からHTTPリクエストのボディを作成します。
     *
     * @param s HTTPリクエストボディの文字列
     * @param charset HTTPリクエストボディの文字セット
     * @return HTTPリクエストボディ
     */
    protected final HttpEntity createHttpEntity(String s, String charset) {
        try {
            return createHttpEntity(s.getBytes(charset), httpRequestContentType);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * コンテンツタイプと文字セットを指定し、文字列からHTTPリクエストのボディを作成します。
     *
     * @param s HTTPリクエストボディの文字列
     * @param charset HTTPリクエストボディの文字セット
     * @param contentType HTTPリクエストボディのコンテンツタイプ
     * @return HTTPリクエストボディ
     */
    protected final HttpEntity createHttpEntity(String s, String charset, ContentType contentType) {
        try {
            return createHttpEntity(s.getBytes(charset), contentType);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 文字セットを指定し、文字列からHTTPリクエストのボディを作成します。
     *
     * @param s HTTPリクエストボディの文字列
     * @param charset HTTPリクエストボディの文字セット
     * @return HTTPリクエストボディ
     */
    protected final HttpEntity createHttpEntity(String s, Charset charset) {
        return createHttpEntity(s.getBytes(charset), httpRequestContentType);
    }

    /**
     * コンテンツタイプと文字セットを指定し、文字列からHTTPリクエストのボディを作成します。
     *
     * @param s HTTPリクエストボディの文字列
     * @param charset HTTPリクエストボディの文字セット
     * @param contentType HTTPリクエストボディのコンテンツタイプ
     * @return HTTPリクエストボディ
     */
    protected final HttpEntity createHttpEntity(String s, Charset charset, ContentType contentType) {
        return createHttpEntity(s.getBytes(charset), contentType);
    }

    /**
     * コンテンツタイプを指定し、文字列からHTTPリクエストのボディを作成します。
     *
     * @param s HTTPリクエストボディの文字列
     * @param contentType HTTPリクエストボディのコンテンツタイプ
     * @return HTTPリクエストボディ
     */
    protected final HttpEntity createHttpEntity(String s, ContentType contentType) {
        return createHttpEntity(s.getBytes(defaultCharset), contentType);
    }

    /**
     * バイト配列からHTTPリクエストのボディを作成します。
     *
     * @param b HTTPリクエストボディのバイト配列
     * @return HTTPリクエストボディ
     */
    protected final HttpEntity createHttpEntity(byte[] b) {
        return createHttpEntity(b, httpRequestContentType);
    }

    /**
     * コンテンツタイプを指定し、バイト配列からHTTPリクエストのボディを作成します。
     *
     * @param b HTTPリクエストボディのバイト配列
     * @param contentType HTTPリクエストボディのコンテンツタイプ
     * @return HTTPリクエストボディ
     */
    protected final HttpEntity createHttpEntity(byte[] b, ContentType contentType) {
        return new ByteArrayEntity(b,  contentType);
    }

    /**
     * プラットフォーム APIのレスポンスデータ構造を表すインスタンスへ変換します。
     *
     * @param httpResponse プラットフォームのHTTPレスポンス
     * @return プラットフォームレスポンスのデータ構造インスタンス
     */
    protected abstract PlatformResponseModel toPlatformResponse(CloseableHttpResponse httpResponse);

    /**
     * HTMLフォームデータをユーザーリクエスト構造へ変換します。
     *
     * @param formData HTMLフォームデータ
     * @return ユーザーリクエストの構造
     */
    protected abstract UserRequestModel toUserRequest(MultivaluedMap<String, String> formData);

    /**
     * ユーザーリクエスト構造をプラットフォームリクエスト構造へ変換します。
     *
     * @param user ユーザーリクエスト構造のインスタンス
     * @return プラットフォームリクエストの構造
     */
    protected abstract PlatformRequestModel toPlatformRequest(UserRequestModel user);

    @Override
    public void init(String apiRootUri, MultivaluedMap<String, String> formData, String idpSender){
        try {
            this.apiRootUri = new URI(apiRootUri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        userRequest = toUserRequest(formData);
        platformRequestSender = Objects.isNull(idpSender) ? "" : idpSender;
    }

    @Override
    public abstract void action();

    @Override
    public String getUserActionMode() {
        return userRequest.getActionMode();
    }

    @Override
    public abstract void ensureHasUniqueId();

    @Override
    public abstract UserModel addUserModelAttributes(UserModel user);
}
