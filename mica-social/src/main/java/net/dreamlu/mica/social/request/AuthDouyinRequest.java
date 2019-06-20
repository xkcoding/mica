package net.dreamlu.mica.social.request;

import com.fasterxml.jackson.databind.JsonNode;
import net.dreamlu.http.HttpRequest;
import net.dreamlu.mica.social.config.AuthConfig;
import net.dreamlu.mica.social.config.AuthSource;
import net.dreamlu.mica.social.exception.AuthException;
import net.dreamlu.mica.social.model.AuthResponse;
import net.dreamlu.mica.social.model.AuthToken;
import net.dreamlu.mica.social.model.AuthUser;
import org.springframework.web.util.UriComponentsBuilder;


/**
 * 抖音登录
 *
 * @author yadong.zhang (yadong.zhang0415(a)gmail.com), L.cm
 */
public class AuthDouyinRequest extends BaseAuthRequest {

	public AuthDouyinRequest(AuthConfig config) {
		super(config, AuthSource.DOUYIN);
	}

	@Override
	public String authorize(String state) {
		return UriComponentsBuilder.fromUriString(authSource.authorize())
			.queryParam("response_type", "code")
			.queryParam("client_key", config.getClientId())
			.queryParam("redirect_uri", config.getRedirectUri())
			.queryParam("state", state)
			.queryParam("scope", "user_info")
			.build()
			.toUriString();
	}

	@Override
	protected AuthToken getAccessToken(String code) {
		JsonNode jsonNode = doPostAuthorizationCode(code).asJsonNode();
		return this.getToken(jsonNode);
	}

	@Override
	protected AuthUser getUserInfo(AuthToken authToken) {
		String accessToken = authToken.getAccessToken();
		String openId = authToken.getOpenId();
		JsonNode object = HttpRequest.get(authSource.userInfo())
			.query("access_token", accessToken)
			.query("open_id", openId)
			.execute()
			.asJsonNode();
		JsonNode userInfoObject = this.checkResponse(object);
		return AuthUser.builder()
			.uuid(userInfoObject.get("open_id").asText())
			.username(userInfoObject.get("nickname").asText())
			.nickname(userInfoObject.get("nickname").asText())
			.avatar(userInfoObject.get("avatar").asText())
			.token(authToken)
			.source(authSource)
			.build();
	}

	@Override
	public AuthResponse refresh(AuthToken oldToken) {
		JsonNode jsonNode = HttpRequest.post(authSource.refresh())
			.query("client_key", config.getClientId())
			.query("refresh_token", oldToken.getRefreshToken())
			.query("grant_type", "refresh_token")
			.execute()
			.asJsonNode();
		return AuthResponse.builder()
			.code(ResponseStatus.SUCCESS.getCode())
			.data(this.getToken(jsonNode))
			.build();
	}

	/**
	 * 检查响应内容是否正确
	 *
	 * @param object 请求响应内容
	 * @return 实际请求数据的json对象
	 */
	private JsonNode checkResponse(JsonNode object) {
		String message = object.get("message").asText();
		JsonNode data = object.get("data");
		int errorCode = data.get("error_code").asInt();
		if ("error".equals(message) || errorCode != 0) {
			throw new AuthException(errorCode, data.get("description").asText());
		}
		return data;
	}

	/**
	 * 获取token，适用于获取access_token和刷新token
	 *
	 * @param object JsonNode
	 * @return token对象
	 */
	private AuthToken getToken(JsonNode object) {
		JsonNode accessTokenObject = this.checkResponse(object);
		return AuthToken.builder()
			.accessToken(accessTokenObject.get("access_token").asText())
			.openId(accessTokenObject.get("open_id").asText())
			.expireIn(accessTokenObject.get("expires_in").asInt())
			.refreshToken(accessTokenObject.get("refresh_token").asText())
			.scope(accessTokenObject.get("scope").asText())
			.build();
	}
}