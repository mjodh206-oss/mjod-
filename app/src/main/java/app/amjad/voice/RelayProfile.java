package app.amjad.voice;

import android.content.Context;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.net.URI;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** A relay can be activated after installation without entering any text. */
final class RelayProfile {
    private static final String ALIAS="voice-relay-grant-v1", STORE="voice-relay";
    private static final String GRANT="v1\\.\\d{9,11}\\.[A-Za-z0-9_-]{24}\\.[A-Za-z0-9_-]{43}";
    final String url, token;
    private RelayProfile(String url,String token){this.url=url;this.token=token;}
    static RelayProfile fromLink(Uri link){
        try{
            if(link==null||!"instant-interpreter".equals(link.getScheme())||!"connect".equals(link.getAuthority())||!"/".equals(link.getPath()))return null;
            String endpoint=link.getQueryParameter("relay"),grant=link.getQueryParameter("grant");
            if(grant==null)grant=link.getFragment();
            if(!isUrl(endpoint)||grant==null||!grant.matches(GRANT)||expired(grant))return null;
            return new RelayProfile(endpoint,grant);
        }catch(RuntimeException e){return null;}
    }
    private static boolean expired(String grant){return Long.parseLong(grant.split("\\.")[1])<=System.currentTimeMillis()/1000;}
    private static boolean isUrl(String url){
        if(url==null||url.length()>300)return false;
        try{URI u=new URI(url);return "wss".equals(u.getScheme())&&u.getHost()!=null&&u.getPort()<=65535&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null&&"/relay".equals(u.getRawPath());}catch(Exception e){return false;}
    }
    static RelayProfile current(Context c){
        try{
            android.content.SharedPreferences prefs=c.getSharedPreferences(STORE,Context.MODE_PRIVATE);
            String url=prefs.getString("url",""),encrypted=prefs.getString("grant","");
            if(!url.isEmpty()||!encrypted.isEmpty()){
                if(!isUrl(url)||encrypted.isEmpty())return null;
                String[] parts=encrypted.split("\\.");if(parts.length!=2)return null;
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
                cipher.updateAAD(url.getBytes(StandardCharsets.UTF_8));
                String token=new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
                return token.matches(GRANT)&&!expired(token)?new RelayProfile(url,token):null;
            }
        }catch(Exception e){return null;}
        String url=InterpreterService.value(c,"relay_url"),token=InterpreterService.value(c,"relay_token");
        return isUrl(url)&&token.matches("[A-Za-z0-9_-]{24,256}")?new RelayProfile(url,token):null;
    }
    String host(){return URI.create(url).getHost();}
    boolean check(){
        HttpsURLConnection connection=null;
        try{
            URI endpoint=URI.create(url);
            connection=(HttpsURLConnection)new URL("https",endpoint.getHost(),endpoint.getPort(),"/relay/check").openConnection();
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
            connection.setRequestProperty("Authorization","Bearer "+token);
            return connection.getResponseCode()==204;
        }catch(Exception e){return false;}finally{if(connection!=null)connection.disconnect();}
    }
    boolean save(Context c){
        try{
            if(expired(token))return false;
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));
            cipher.updateAAD(url.getBytes(StandardCharsets.UTF_8));
            byte[] out=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            String encoded=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+"."+Base64.encodeToString(out,Base64.NO_WRAP);
            return c.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().putString("url",url).putString("grant",encoded).commit();
        }catch(Exception e){return false;}
    }
    static void clear(Context c){c.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().clear().apply();}
    private static SecretKey key(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        if(!create)throw new IllegalStateException();
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return gen.generateKey();
    }
}
