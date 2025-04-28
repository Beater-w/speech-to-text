import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class FileTransJavaDemo {
    // 地域ID，常量，固定值。
    public static final String REGIONID = "cn-shanghai";
    public static final String ENDPOINTNAME = "cn-shanghai";
    public static final String PRODUCT = "nls-filetrans";
    public static final String DOMAIN = "filetrans.cn-shanghai.aliyuncs.com";
    public static final String API_VERSION = "2018-08-17";  // 中国站版本

    public static final String POST_REQUEST_ACTION = "SubmitTask";
    public static final String GET_REQUEST_ACTION = "GetTaskResult";

    // 请求参数
    public static final String KEY_APP_KEY = "appkey";
    public static final String KEY_FILE_LINK = "file_link";
    public static final String KEY_VERSION = "version";
    public static final String KEY_ENABLE_WORDS = "enable_words";

    // 响应参数
    public static final String KEY_TASK = "Task";
    public static final String KEY_TASK_ID = "TaskId";
    public static final String KEY_STATUS_TEXT = "StatusText";
    public static final String KEY_RESULT = "Result";

    // 状态值
    public static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_QUEUEING = "QUEUEING";

    // 阿里云鉴权client
    IAcsClient client;

    public FileTransJavaDemo(String accessKeyId, String accessKeySecret) {
        // 设置endpoint
        try {
            DefaultProfile.addEndpoint(ENDPOINTNAME, REGIONID, PRODUCT, DOMAIN);
        } catch (ClientException e) {
            e.printStackTrace();
        }
        // 创建DefaultAcsClient实例并初始化
        DefaultProfile profile = DefaultProfile.getProfile(REGIONID, accessKeyId, accessKeySecret);
        this.client = new DefaultAcsClient(profile);
    }

    public String submitFileTransRequest(String appKey, String fileLink) {
        CommonRequest postRequest = new CommonRequest();
        postRequest.setDomain(DOMAIN);
        postRequest.setVersion(API_VERSION);
        postRequest.setAction(POST_REQUEST_ACTION);
        postRequest.setProduct(PRODUCT);

        JSONObject taskObject = new JSONObject();
        taskObject.put(KEY_APP_KEY, appKey);
        taskObject.put(KEY_FILE_LINK, fileLink);
        taskObject.put(KEY_VERSION, "4.0");
        taskObject.put(KEY_ENABLE_WORDS, true);
        taskObject.put("enable_sample_rate_adaptive", true);


        String task = taskObject.toJSONString();
        System.out.println("提交的任务内容：" + task);

        postRequest.putBodyParameter(KEY_TASK, task);
        postRequest.setMethod(MethodType.POST);

        String taskId = null;
        try {
            CommonResponse postResponse = client.getCommonResponse(postRequest);
            System.err.println("提交录音文件识别请求的响应：" + postResponse.getData());
            if (postResponse.getHttpStatus() == 200) {
                JSONObject result = JSONObject.parseObject(postResponse.getData());
                String statusText = result.getString(KEY_STATUS_TEXT);
                if (STATUS_SUCCESS.equals(statusText)) {
                    taskId = result.getString(KEY_TASK_ID);
                }
            }
        } catch (ClientException e) {
            e.printStackTrace();
        }
        return taskId;
    }

    public String getFileTransResult(String taskId) {
        CommonRequest getRequest = new CommonRequest();
        getRequest.setDomain(DOMAIN);
        getRequest.setVersion(API_VERSION);
        getRequest.setAction(GET_REQUEST_ACTION);
        getRequest.setProduct(PRODUCT);
        getRequest.putQueryParameter(KEY_TASK_ID, taskId);
        getRequest.setMethod(MethodType.GET);

        String result = null;
        while (true) {
            try {
                CommonResponse getResponse = client.getCommonResponse(getRequest);
                System.err.println("识别查询结果：" + getResponse.getData());
                if (getResponse.getHttpStatus() != 200) {
                    break;
                }
                JSONObject rootObj = JSONObject.parseObject(getResponse.getData());
                String statusText = rootObj.getString(KEY_STATUS_TEXT);
                if (STATUS_RUNNING.equals(statusText) || STATUS_QUEUEING.equals(statusText)) {
                    Thread.sleep(10000);
                } else {
                    if (STATUS_SUCCESS.equals(statusText)) {
                        result = rootObj.getString(KEY_RESULT);
                        if (result == null) {
                            result = "";
                        }
                    }
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private static void saveResultToTxt(String resultJson, String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            JSONObject jsonObject = JSONObject.parseObject(resultJson);
            JSONArray sentences = jsonObject.getJSONArray("Sentences");

            if (sentences != null) {
                for (int i = 0; i < sentences.size(); i++) {
                    JSONObject sentence = sentences.getJSONObject(i);
                    String text = sentence.getString("Text");
                    writer.write(text);
                    writer.newLine();
                }
                System.out.println("识别文本成功保存到文件：" + filePath);
            } else {
                System.out.println("未找到'Sentences'字段，保存原始内容。");
                writer.write(resultJson);
            }
        } catch (IOException e) {
            System.err.println("保存识别结果到文件失败！");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        final String accessKeyId = System.getenv().get("ALIYUN_AK_ID");
        final String accessKeySecret = System.getenv().get("ALIYUN_AK_SECRET");
        final String appKey = System.getenv().get("NLS_APP_KEY");

        //文件路径，这里需要修改
        String fileLink = "https://gw.alipayobjects.com/os/bmw-prod/0574ee2e-f494-45a5-820f-63aee583045a.wav";

        FileTransJavaDemo demo = new FileTransJavaDemo(accessKeyId, accessKeySecret);

        String taskId = demo.submitFileTransRequest(appKey, fileLink);
        if (taskId != null) {
            System.out.println("录音文件识别请求成功，task_id: " + taskId);
        } else {
            System.out.println("录音文件识别请求失败！");
            return;
        }

        String result = demo.getFileTransResult(taskId);
        if (result != null) {
            System.out.println("录音文件识别结果查询成功！");
            // 保存到txt
            saveResultToTxt(result, "speech_result.txt");
        } else {
            System.out.println("录音文件识别结果查询失败！");
        }
    }
}
