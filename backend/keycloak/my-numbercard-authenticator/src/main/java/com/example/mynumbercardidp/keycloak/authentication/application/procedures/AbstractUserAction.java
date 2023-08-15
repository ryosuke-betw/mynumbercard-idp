package com.example.mynumbercardidp.keycloak.authentication.application.procedures;

import com.example.mynumbercardidp.keycloak.authentication.authenticators.browser.SpiConfigProperty;
import com.example.mynumbercardidp.keycloak.network.platform.UserRequestModel;
import com.example.mynumbercardidp.keycloak.network.platform.PlatformResponseModel;
import com.example.mynumbercardidp.keycloak.core.authentication.application.procedures.ApplicationProcedure;
import com.example.mynumbercardidp.keycloak.core.network.platform.PlatformApiClientImpl;
import com.example.mynumbercardidp.keycloak.util.authentication.CurrentConfig;
import com.example.mynumbercardidp.keycloak.util.StringUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.jboss.logging.Logger;
import org.keycloak.authentication.authenticators.x509.UserIdentityToModelMapper;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.UserModel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.InvalidKeyException;
import java.util.Base64;
import javax.ws.rs.core.Response;

/**
 * ユーザーが希望する操作の抽象クラスです。
 *
 * 認証、登録、登録情報の変更などで実行される処理のうち、共通の処理を定義します。
 */
public abstract class AbstractUserAction implements ApplicationProcedure {

    private static final String MESSAGE_DEBUG_MODE_ENABLED = "Debug mode is enabled. ";
    private final Logger consoleLogger = Logger.getLogger(AbstractUserAction.class);

    /**
     * 認証ユーザーのセッション情報からNonce文字列を取得します。
     *
     * AuthNote名のnonceはActionHandler呼び出し前に上書きされるため、
     * AuthNoteからverifyNonceを取得します。
     * @param constext 認証フローのコンテキスト
     * @return Nonce文字列
     */
    private static String getNonce(AuthenticationFlowContext context) {
       String authNoteName = "nonce";
       return context.getAuthenticationSession().getAuthNote(authNoteName);
    }

    /**
     * 指定された文字列をSHA256アルゴリズムでハッシュ化し、その文字列を返します。
     *
     * @param str SHA256アルゴリズムでハッシュ化する文字列
     * @return SHA256アルゴリズムでハッシュ化された文字列
     */
    private static String toHashString(final String str) {
        return DigestUtils.sha256Hex(str);
    }

    /**
     * 事前処理として、署名された文字列がNonceをハッシュ化した文字列かどうか検証します。
     * 
     * @param constext 認証フローのコンテキスト
     * @param platform プラットフォーム APIクライアントのインスタンス
     */
    @Override
    public void preAction(final AuthenticationFlowContext context, final PlatformApiClientImpl platform) {
        UserRequestModel user = (UserRequestModel) platform.getUserRequest();
        user.ensureHasValues();
        tryValidateSignature(context, platform);
        platform.action();
    }

    @Override
    public void onAction(final AuthenticationFlowContext context, final PlatformApiClientImpl platform) {

    }

    @Override
    public void postAction(final AuthenticationFlowContext context, final PlatformApiClientImpl platform) {

    }

    /**
     * プラットフォームが返したユニークIDからKeycloak内のユーザーを返します。
     * 
     * @param constext 認証フローのコンテキスト
     * @param uniqueId プラットフォームが識別したユーザーを特定する一意の文字列
     * @param str 署名された文字列
     * @return ユーザーのデータ構造 Keycloak内のユーザーが見つかった場合はユーザーデータ構造、そうでない場合はNull
     */
    protected UserModel findUser(final AuthenticationFlowContext context, final String uniqueId) {
        String userAttributeUniqueIdName = "uniqueId";
        try {
            return UserIdentityToModelMapperBuilder.fromString(userAttributeUniqueIdName).find(context, uniqueId);
        } catch (Exception e) {
            // 報告された例外はExceptionクラスで詳細な判別がつかない。
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * デバッグモードの状態を返します。
     *
     * @param constext 認証フローのコンテキスト
     * @return 有効の場合はtrue、そうでない場合はfalse
     */ 
    protected boolean isDebugMode(final AuthenticationFlowContext context) {
        String debugMode = SpiConfigProperty.DebugMode.CONFIG.getName();
        String debugModeValue = CurrentConfig.getValue(context, debugMode).toLowerCase();
        debugModeValue = StringUtil.isEmpty(debugModeValue) ? "false" : debugModeValue.toLowerCase();
        return Boolean.valueOf(debugModeValue);
    }

    /**
     * プラットフォームのレスポンスデータからユーザーのユニークIDを抽出します。
     *
     * 空の場合は IllegalStateException を送出します。
     * @param response プラットフォームのレスポンス
     * @return ユーザーの一意のID
     */
    protected String tryExtractUniqueId(final PlatformResponseModel response) {
        response.ensureHasUniqueId();
        return response.getUniqueId();
    }

    /**
     * ユーザーリクエストからnonceをハッシュ化した値を抽出します。
     *
     * @param request ユーザーリクエストのデータ構造
     */
    private String extractApplicantData(final PlatformApiClientImpl platform) {
        UserRequestModel user = (UserRequestModel) platform.getUserRequest();
        return user.getApplicantData();
    }

    /**
     * ユーザーリクエストから公開鍵を抽出します。
     *
     * @param request ユーザーリクエストのデータ構造
     */
    private String extractCertificate(final PlatformApiClientImpl platform) {
        UserRequestModel user = (UserRequestModel) platform.getUserRequest();
        return user.getCertificate();
    }

    /**
     * ユーザーリクエストから署名した値を抽出します。
     *
     * @param request ユーザーリクエストのデータ構造
     */
    private String extractSign(final PlatformApiClientImpl platform) {
        UserRequestModel user = (UserRequestModel) platform.getUserRequest();
        return user.getSign();
    }

    /**
     * ユーザーリクエストから公開鍵とnonceを利用して、署名した値が文字列と一致するかを検証します。
     *
     * @param constext 認証フローのコンテキスト
     */
     // Keycloakが発行したnonceをハッシュ化した値とユーザーが自己申告したnonceをハッシュ化した値は異なる可能性がある。
     // デバッグモードが無効の場合、ユーザーが送ってきたnonceをハッシュ化した値は信用しない。
    private void tryValidateSignature(final AuthenticationFlowContext context, final PlatformApiClientImpl platform) {
        String nonce = AbstractUserAction.getNonce(context);
        consoleLogger.debug("Nonce: " + nonce);
        String nonceHash = AbstractUserAction.toHashString(nonce);
        consoleLogger.debug("Nonce hash: " + nonceHash);

        String applicantData = extractApplicantData(platform);
        consoleLogger.debug("Applicant data: " + applicantData);
        String applicantDataLower = applicantData.toLowerCase();
        String applicantDataUpper = applicantData.toUpperCase();

        if (!nonceHash.equals(applicantDataLower) && !nonceHash.equals(applicantDataUpper)) {
            String message ="Applicant data is not equals a nonce hash.";
            if (!isDebugMode(context)) {
                throw new IllegalArgumentException(message);
            }
            consoleLogger.info(MESSAGE_DEBUG_MODE_ENABLED + message);
        }

        String certificate = extractCertificate(platform);
        String sign = extractSign(platform);
        if (!isDebugMode(context)) {
            if (!validateSignature(sign, certificate, nonceHash.toLowerCase()) ||
                !validateSignature(sign, certificate, nonceHash.toUpperCase())) {
                    String message = "The signature is not equals a nonce hash.";
                    throw new IllegalArgumentException(message);
            }
        }
        validateSignature(sign, certificate, nonceHash, nonce, applicantData);
    }

    /**
     * 公開鍵とnonceを利用して、署名した値が文字列と一致するかを検証します。
     *
     * 検査例外が発生した場合、非検査例外でラップし送出します。
     *
     * @param signature X.509に準拠する鍵で文字列に署名した結果
     * @param certificateBase64Content 公開鍵を基本型Base64でエンコードした値
     * @param str 署名された文字列
     * @return 検証された場合はtrue、そうでない場合はfalse
     * @exception UncheckedIOException 公開鍵の値が空値の場合
     * @exception IllegalArgumentException 署名の検証中に例外が発生した場合
     */
    private boolean validateSignature(final String signature, final String certificateBase64Content, final String str) {
        String charset = "utf-8";
        String certType = "X.509";
        try {
            byte[] certificateBinary = Base64.getDecoder().decode(certificateBase64Content.getBytes(charset));
            Certificate certificate = null;
            try (InputStream inputStream = new ByteArrayInputStream(certificateBinary)) {
                certificate = CertificateFactory.getInstance(certType).generateCertificate(inputStream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            String signAlgorithm = "SHA256withRSA";
            Signature engine = Signature.getInstance(signAlgorithm);
            engine.initVerify(certificate);
            engine.update(str.getBytes(charset));
            return engine.verify(Base64.getDecoder().decode(signature.getBytes(charset)));
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException | CertificateException |
                 InvalidKeyException | SignatureException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 公開鍵とnonceを利用して、署名した値が文字列と一致するかを検証します。
     *
     * nonceをハッシュ化した値、nonce、ユーザーが自己申告した値の順で検証します。
     *
     * @param signature X.509に準拠する鍵で文字列に署名した結果
     * @param certificateBase64Content 公開鍵を基本型Base64でエンコードした値
     * @param nonceHash nonceをハッシュ化した文字列
     * @param nonce ランダムに生成された文字列
     * @param applicantData ユーザーが自己申告した文字列
     */
    private void validateSignature(String signature, String certificateBase64Content, String nonceHash, String nonce, String applicantData) {
        if (validateSignature(signature, certificateBase64Content, nonceHash.toLowerCase()) ||
            validateSignature(signature, certificateBase64Content, nonceHash.toUpperCase())) {
            return;
        }

        String consoleMessage = "Failed validate signature. The signed value was not a nonce hash. Retry, verifies that the signed value is a nonce.";
        consoleLogger.info(MESSAGE_DEBUG_MODE_ENABLED + consoleMessage);
        if (validateSignature(signature, certificateBase64Content, nonce)) {
            return;
        }

        consoleMessage = "Failed validate signature. The signed value was not a nonce.";
        consoleLogger.info(consoleMessage);
        if (validateSignature(signature, certificateBase64Content, applicantData)) {
            return;
        }
        String message = "The signature is not equals a applicant data.";
        throw new IllegalArgumentException(message);
    }

    // /**
    //  * 公的個人認証部分をプラットフォームへ送信し、その結果を返します。
    //  *
    //  * @param platform プラットフォーム APIクライアントのインスタンス
    //  */
    // private PlatformResponseModel platformPost(final PlatformApiClientImpl platform) {
    //     platform.action();
    //     return (PlatformResponseModel) platform.getPlatformResponse();
    // }

    /**
     *  ユーザー属性項目と値の組み合わせからユーザーを返す処理の定義です。
     */
    private static class UserIdentityToModelMapperBuilder {

        private static UserIdentityToModelMapper fromUniqueId() {
            String attributeName = "uniqueId";
            return fromString(attributeName);
        }

        private static UserIdentityToModelMapper fromString(final String attributeName) {
            UserIdentityToModelMapper mapper = UserIdentityToModelMapper.getUserIdentityToCustomAttributeMapper(attributeName);
            return mapper;
        }
    }
}