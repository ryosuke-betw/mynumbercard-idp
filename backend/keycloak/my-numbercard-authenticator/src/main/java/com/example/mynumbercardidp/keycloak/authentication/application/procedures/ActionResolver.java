package com.example.mynumbercardidp.keycloak.authentication.application.procedures;

import com.example.mynumbercardidp.keycloak.authentication.application.procedures.user.ActionType;
import com.example.mynumbercardidp.keycloak.authentication.application.procedures.user.LoginAction;
import com.example.mynumbercardidp.keycloak.authentication.application.procedures.user.RegistrationAction;
import com.example.mynumbercardidp.keycloak.core.authentication.application.procedures.AbstractActionResolver;
import com.example.mynumbercardidp.keycloak.core.network.platform.PlatformApiClientInterface;
import org.keycloak.authentication.AuthenticationFlowContext;

/**
 * ユーザーからKeycloakへのHTTPリクエストを元に実行する処理を呼び出すクラスです。
 */
public class ActionResolver extends AbstractActionResolver {
    private static final LoginAction LOGIN_ACTION = new LoginAction();
    private static final RegistrationAction REGISTRATION_ACTION = new RegistrationAction();

    @Override
    public void executeUserAction(final AuthenticationFlowContext context) {
        PlatformApiClientInterface platform = super.createPlatform(context);
        platform.setContextForDataManager(context);
        ActionType userActionMode = Enum.valueOf(ActionType.class,
                platform.getUserRequest().getActionMode().toUpperCase());
        switch (userActionMode) {
            case LOGIN:
                ActionResolver.LOGIN_ACTION.authenticate(context, platform);
                break;
            case REGISTRATION:
                ActionResolver.REGISTRATION_ACTION.register(context, platform);
                break;
            default:
                throw new IllegalArgumentException("Action mode " + userActionMode.getName() + " is the undefined.");
        }
    }
}
