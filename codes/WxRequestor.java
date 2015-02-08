import com.github.kevinsawicki.http.HttpRequest;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import io.terminus.common.redis.utils.JedisTemplate;
import io.terminus.common.utils.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Created by haolin on 1/6/15.
 */
@Slf4j
@SuppressWarnings("all")
@Component
public class WxRequestor {

    private static final String authorizeUrl = "https://open.weixin.qq.com/connect/oauth2/authorize";

    private static final String getOpenIdUrl = "https://api.weixin.qq.com/sns/oauth2/access_token";

    private static final String getTokenUrl = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential";

    private static final String getUserInfoUrl = "https://api.weixin.qq.com/cgi-bin/user/info";

    @Autowired
    private JedisTemplate jedisTemplate;

    @Value(value = "#{app.wxAppId}")
    private String appId;

    @Value(value = "#{app.wxSecret}")
    private String secret;

    private static final JsonMapper jsonMapper = JsonMapper.JSON_NON_DEFAULT_MAPPER;

    /**
     * 发送微信GET请求(默认不带上access_token)
     * @param url 请求url
     * @return map结果集
     */
    public Map<String, Object> get(String url){
        return get(url, Boolean.FALSE);
    }

    /**
     * 发送微信GET请求
     * @param url 请求url
     * @param withAccessToken 是否要带上access_token
     * @return map结果集
     */
    public Map<String, Object> get(String url, boolean withAccessToken){
        Map<String, Object> mapResp = doGet(url, withAccessToken);
        log.info("[weixin]: do get request({}), and get response({}).", url, mapResp);
        Object errcode = mapResp.get("errcode");
        if (errcode != null && withAccessToken){
            if (Objects.equal("40003", errcode)){
                // token失效
                doGetAccessToken();
                return doGet(url, withAccessToken);
            }
            log.error("[weixin]: failed to do wx request({}), cause: {}", url, mapResp);
        }
        return mapResp;
    }

    private Map<String, Object> doGet(String url, boolean withAccessToken){
        String requestUrl = withAccessToken ? addAccessToken(url) : url;
        String jsonResp = HttpRequest.get(requestUrl).body();
        Map<String, Object> mapResp = jsonMapper.fromJson(jsonResp, Map.class);
        return mapResp;
    }

    /**
     * 发送微信POST请求
     * @param url 请求url
     * @return map结果集
     */
    public Map<String, Object> post(String url){
        return post(url, Boolean.FALSE);
    }

    /**
     * 发送微信POST请求
     * @param url 请求url
     * @param withAccessToken 发送请求是是否带上access_token
     * @return map结果集
     */
    public Map<String, Object> post(String url, boolean withAccessToken){
        Map<String, Object> mapResp = doPost(url, withAccessToken);
        Object errcode = mapResp.get("errcode");
        if (errcode != null && withAccessToken){
            if (Objects.equal("41001", errcode)){
                // token失效
                doGetAccessToken();
                return doPost(url, withAccessToken);
            }
            log.error("[weixin]: failed to do wx request({}), cause: {}", url, mapResp);
        }
        return mapResp;
    }

    /**
     * 发送微信POST请求
     * @param url 请求url
     * @param withAccessToken 发送请求是是否带上access_token
     * @return map结果集
     */
    private Map<String, Object> doPost(String url,  boolean withAccessToken){
        String requestUrl = withAccessToken ? addAccessToken(url) : url;
        String jsonResp = HttpRequest.post(requestUrl).body();
        Map<String, Object> mapResp = jsonMapper.fromJson(jsonResp, Map.class);
        log.info("[weixin]: do post request({}), and get response({}).", url, mapResp);
        return mapResp;
    }

    /**
     * 链接中加上access_token
     * @param url 请求url
     * @return 添加access_token后的url
     */
    private String addAccessToken(String url) {
        StringBuilder requestUrl = new StringBuilder(url);
        if (requestUrl.indexOf("?") != -1){
            requestUrl.append("&access_token=").append(getAccessToken());
        } else {
            requestUrl.append("?access_token=").append(getAccessToken());
        }
        return requestUrl.toString();
    }

    /**
     * 获取访问token
     * @return access_token
     */
    private String getAccessToken() {
        String token = jedisTemplate.execute(new JedisTemplate.JedisAction<String>() {
            @Override
            public String action(Jedis jedis) {
                return jedis.get(keyOfAccessToken(appId));
            }
        });
        if (Strings.isNullOrEmpty(token)){
            token = doGetAccessToken();
        }
        return token;
    }

    /**
     * 获取access_token
     * @return access_token
     */
    private String doGetAccessToken() {
        StringBuilder requestUrl = new StringBuilder(getTokenUrl);
        requestUrl.append("&appid=").append(appId)
                  .append("&secret=").append(secret);
        Map<String, Object> mapResp = doGet(requestUrl.toString(), Boolean.FALSE);
        if (mapResp.get("errcode") != null){
            log.error("failed to get wx access token, cause: {}", mapResp);
        } else {
            String token = String.valueOf(mapResp.get("access_token"));
            Integer expires;
            try {
                expires = Integer.valueOf(String.valueOf(mapResp.get("expires_in")));
            } catch (NumberFormatException e){
                expires = 3600;
            }
            doSaveToken(token, expires);
            return token;
        }
        return null;
    }

    private void doSaveToken(final String token, final Integer expires) {
        if (!Strings.isNullOrEmpty(token)){
            jedisTemplate.execute(new JedisTemplate.JedisActionNoResult() {
                @Override
                public void action(Jedis jedis) {
                    String key = keyOfAccessToken(appId);
                    jedis.setex(key, expires, token);
                }
            });
        }
    }

    private String keyOfAccessToken(String appId) {
        return "wxac:"+appId;
    }

    /**
     * 重定向微信认证页面
     */
    public void toAuthorize(String redirectUrl, HttpServletResponse response) throws IOException {
       toAuthorize(redirectUrl, "123", response);
    }

    /**
     * 重定向微信认证页面
     */
    public void toAuthorize(String redirectUrl, String state, HttpServletResponse response) throws IOException {
        String encodedUrl = URLEncoder.encode(redirectUrl, "utf-8");
        StringBuilder redirect = new StringBuilder(authorizeUrl);
        redirect.append("?appid=").append(appId)
                .append("&redirect_uri=").append(encodedUrl)
                .append("&response_type=code&scope=snsapi_base")
                .append("&state=").append(state)
                .append("#wechat_redirect");
        log.info("[weixin]: go to authorize page : {}.", redirect);
        response.sendRedirect(redirect.toString());
    }

    /**
     * 获取微信用户的openid
     * @param code 认证后的code值
     * @return 获取成功openid存放在map中, 反之errcode为错误码
     */
    public Map<String, Object> getOpenId(String code){
        StringBuilder url = new StringBuilder(getOpenIdUrl);
        url.append("?appid=").append(appId)
                .append("&secret=").append(secret)
                .append("&code=").append(code)
                .append("&grant_type=authorization_code");
        return get(url.toString());
    }

    /**
     * 获取微信用户信息, 若用户未关注公众号, 将获取不到用户信息
     * @param openId 用户openid
     * @return 微信用户信息, 若用户未关注公众号, 将获取不到用户信息
     */
    public Map<String, Object> getUserInfo(String openId){
        return doPost(getUserInfoUrl + "?openid=" + openId, Boolean.TRUE);
    }
}
