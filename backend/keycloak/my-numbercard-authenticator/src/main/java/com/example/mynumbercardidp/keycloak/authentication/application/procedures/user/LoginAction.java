package com.example.mynumbercardidp.keycloak.authentication.application.procedures.user;

import com.example.mynumbercardidp.keycloak.authentication.application.procedures.AbstractUserAction;
import com.example.mynumbercardidp.keycloak.authentication.application.procedures.ResponseCreater;
import com.example.mynumbercardidp.keycloak.network.platform.PlatformResponseModel;
import com.example.mynumbercardidp.keycloak.core.network.platform.PlatformApiClientImpl;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.UserModel;

import java.util.Objects;
import javax.ws.rs.core.Response;

/**
 * 個人番号カードの公的個人認証部分を利用したプラットフォームからの応答を元に認証処理をし、ユーザーへのレスポンスを設定する定義です。
 */
public class LoginAction extends AbstractUserAction {
    /**
     * 公的個人認証部分をプラットフォームへ送信し、その応答からユーザーを認証します。
     *
     * @param context 認証フローのコンテキスト
     * @param platform プラットフォーム APIクライアントのインスタンス
     */
    @Override
    public void onAction(final AuthenticationFlowContext context, final PlatformApiClientImpl platform) { 
        PlatformResponseModel response = (PlatformResponseModel) platform.getPlatformResponse();
        int platformStatusCode = response.getHttpStatusCode();
        if (! new LoginFlowTransition().canAction(context, Response.Status.fromStatusCode(platformStatusCode))) {
            return;
        }

        /*
         * ユニークIDからKeycloak内のユーザーを探す。
         * Keycloak内にユーザーが存在しない場合は登録画面を表示する。
         */
        String uniqueId = super.tryExtractUniqueId(response);
        UserModel user = super.findUser(context, uniqueId);
        if (Objects.isNull(user)) {
            ResponseCreater.actionRegistrationChallenge(context);
            return;
        }

        context.setUser(user);
        context.success();
    } 
}
