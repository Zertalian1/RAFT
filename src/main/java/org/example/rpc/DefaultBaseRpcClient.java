package org.example.rpc;

import com.alipay.remoting.exception.RemotingException;
import com.alipay.remoting.rpc.RpcClient;
import org.example.rpc.entity.RaftRemotingException;
import org.example.rpc.entity.Request;
import org.example.rpc.entity.Response;


public class DefaultBaseRpcClient implements BaseRpcClient {
    private final static RpcClient client = new RpcClient();

    static {
        client.startup();
    }


    @Override
    public Response send(Request request) {
        return send(request, 200000);
    }

    @Override
    public Response send(Request request, int timeout) {
        Response result = null;
        try {
            result = (Response) client.invokeSync(request.getUrl(), request, timeout);
        } catch (RemotingException e) {
            /*если узел не работает, то он не сможет установить связь*/
            throw new RaftRemotingException();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }
}
