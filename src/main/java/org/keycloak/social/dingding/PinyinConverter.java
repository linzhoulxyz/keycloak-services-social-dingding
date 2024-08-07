package org.keycloak.social.dingding;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.logging.Logger;
import java.util.logging.Level;

public class PinyinConverter {
    private final static Logger LOGGER = Logger.getLogger(PinyinConverter.class.getName());

    // 拼音转换服务运行在同一个容器里面
    public static final String CONVERT_URL = "http://127.0.0.1:28080/topinyin";

    public static String convertToPinyin(String username) {
        String apiUrl = createPinyinConvertUrl(username);
        HttpURLConnection connection = null;
        StringBuilder responseContent = new StringBuilder();

        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();

            // 设置请求方式
            connection.setRequestMethod("GET");

            // 连接超时和读取超时设置
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // 创建输入流读取响应
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                responseContent.append(line);
            }
            reader.close();

            // 解析JSON数据
            String pinyin = responseContent.toString();
            LOGGER.info("拼音转换结果是：" + pinyin);
            return pinyin;

        } catch (Exception e) {
            e.printStackTrace();
            return null; // 在实际应用中应该处理异常或者返回更详细的错误信息
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected static String createPinyinConvertUrl(String text) {
        LOGGER.info("Creating Pinyin Convert Url...");

        String url = CONVERT_URL + "?text=" + text;

        LOGGER.info("拼音转换链接是：" + url);
        return url;
    }

}
