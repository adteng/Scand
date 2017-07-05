package com.teng.scand;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ZoomControls;


public class MainActivity extends Activity  implements SurfaceHolder.Callback {

    private Camera mCamera;// Camera����
    private SurfaceView mSurfaceView;// ��ʾͼ���surfaceView
    private SurfaceHolder holder;// SurfaceView�Ŀ�����
    private MyAutoFocusCallback mAutoFocusCallback = new MyAutoFocusCallback();// AutoFocusCallback�Զ��Խ��Ļص�����
    private String strCaptureFilePath = Environment
            .getExternalStorageDirectory() + "/DCIM/Camera/";// ����ͼ���·��
    
    private byte[] mBuffer = new byte[3224000];
    private int mCurrBufferLen = 0;
    private String m_strLock = "lock";
     
    private boolean m_bFocus = false;
    private SVDraw  mSVDraw = null;
    Thread m_setFocusThread;	
    int m_iSleep = 1000;
    private int m_iMaxZoom = 0;
    private int m_iZoom = 0;
    int m_iSurfaceH;
    int m_iSurfaceW;
    
    Handler m_handler = new Handler(){
        public void handleMessage(Message msg) {
            switch (msg.what) {      
            case 1:
            	Size size = mCamera.getParameters().getPreviewSize();//��ȡԤ����С
            	synchronized(m_strLock)
            	{
            		Log.i("jefry", "w="+size.width+"  h="+size.height);
            		String str = scandFrame(size.width,size.height,mBuffer);
            		String[] s = str.split(",");
            		int iSum = Integer.parseInt(s[0]);
            		if(iSum > 0 && s.length > 4)
            		{
            			//Rect r = new Rect(Integer.parseInt(s[1])*2/3,Integer.parseInt(s[2])*2/3,Integer.parseInt(s[3])*2/3,Integer.parseInt(s[4])*2/3);
            			Rect r = new Rect(Integer.parseInt(s[1])*m_iSurfaceW/size.height,Integer.parseInt(s[2])*m_iSurfaceH/size.width,Integer.parseInt(s[3])*m_iSurfaceW/size.height,Integer.parseInt(s[4])*m_iSurfaceH/size.width);
            			mSVDraw.drawRect(r);
            		}
            		else
            			mSVDraw.clearDraw();
            		Log.i("result", str);	
            		TextView v = (TextView)findViewById(R.id.textView1);
            		v.setText(str);
            	}
            	synchronized (m_setFocusThread)
        		{
        			m_setFocusThread.notifyAll();
        		}
                break;
            default:
            	break;
            }
            super.handleMessage(msg);
        }  
          
    };
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (checkCameraHardware(this)) 
		{
			Log.e("============", "����ͷ����");// ��֤����ͷ�Ƿ����
		}
	    /* ����״̬�� */
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
		/* ���ر����� */
		requestWindowFeature(Window.FEATURE_NO_TITLE);
	    /* �趨��Ļ��ʾΪ���� */
	    // this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	    setContentView(R.layout.activity_main);
	    getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);		
	    //getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
	    /* SurfaceHolder���� */
		mSurfaceView = (SurfaceView) findViewById(R.id.mSurfaceView);
	    holder = mSurfaceView.getHolder();
	    holder.addCallback(this);
	    // holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	    
	    ZoomControls zoomControls = (ZoomControls) findViewById(R.id.zoomControls1);
        zoomControls.setOnZoomInClickListener (new View.OnClickListener() {
                   @Override
                   public void onClick(View v) {
                           if(m_iZoom < m_iMaxZoom)
                           {
                                   m_iZoom = m_iZoom + 1;
                                   android.hardware.Camera.Parameters params = mCamera.getParameters();
                                   params.setZoom(m_iZoom);
                                   mCamera.setParameters(params);
                                   mCamera.autoFocus(mAutoFocusCallback);
                           }
                   }
                  });
        zoomControls.setOnZoomOutClickListener(new View.OnClickListener() {

                   @Override
                   public void onClick(View v) {
                           if(m_iZoom > 0)
                           {
                                   m_iZoom = m_iZoom - 1;
                                   android.hardware.Camera.Parameters params = mCamera.getParameters();
                                   params.setZoom(m_iZoom);
                                   mCamera.setParameters(params);
                                   mCamera.autoFocus(mAutoFocusCallback);
                           }
                   }

                  });
	    
	    
	        
	    mSVDraw = (SVDraw)findViewById(R.id.mDraw);
	    mSVDraw.setVisibility(View.VISIBLE);  
	    m_setFocusThread = new Thread()
	    {
	    	public void run()
	    	{
	    		try {
	    			Thread.sleep(1000);
				} 
	    		catch (InterruptedException e) 
	    		{
	    			// TODO Auto-generated catch block
					e.printStackTrace();
	    		}
        	   	while(mCamera != null)
        	   	{
        	   		Message message = new Message();      
                    message.what = 1;
    	   			try 
    	   			{
    	   				synchronized (m_setFocusThread) 
    	   				{
    	   					m_handler.sendMessage(message);
    	   					m_setFocusThread.wait();
    	   				}
    	   				Thread.sleep(m_iSleep);
    	   			} 
    	   			catch (InterruptedException e) 
    	   			{
    	   				// TODO Auto-generated catch block
    	   				e.printStackTrace();
    	   			}
        	   	}
	    	}
	    }; 
	    setJNIEnv("/storage/sdcard1/carnumber/65_car/65_car");
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,int height) 
	{
		// TODO Auto-generated method stub
		m_iSurfaceH = height;
		m_iSurfaceW = width;
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) 
	{
		// TODO Auto-generated method stub
        try 
        {
            mCamera = null;
            try 
            {
                mCamera = Camera.open();//Camera.open(0);//��������ڵͰ汾�ֻ��open�����������߼��汾����˷����������Ǿ��д򿪶��
                //������������������������Ϊ������ı��
                //��manifest���趨����С�汾��Ӱ�����﷽���ĵ��ã������С�汾�趨���󣨰汾���ͣ�����ide�ｫ����������вε�
                //open����;
                //���ģ�����汾�ϸߵĻ����޲ε�open����������nullֵ!���Ծ���ʹ��ͨ�ð汾��ģ������API��
            } 
            catch (Exception e) 
            {
                Log.e("============", "����ͷ��ռ��");
            }
            if (mCamera == null) 
            {
                Log.e("============", "�����Ϊ��");
                System.exit(0);
            }
            mCamera.setPreviewDisplay(holder);//������ʾ��������
            if(this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
            	mCamera.setDisplayOrientation(90);
            else
            	mCamera.setDisplayOrientation(0);  
            android.hardware.Camera.Parameters params = mCamera.getParameters();
            
            m_iMaxZoom = params.getMaxZoom();

            //params.setPreviewFormat(ImageFormat.JPEG);
            params.setPreviewFormat(ImageFormat.NV21);
            //params.setPictureSize(480,640);
            params.setPreviewSize(960, 720);
            
            /*List<String> focusModes = params.getSupportedFocusModes();  
            if(focusModes.contains("continuous-video")){  
            	params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);  
            }*/
            mCamera.setParameters(params);
            priviewCallBack pre = new priviewCallBack();//����Ԥ���ص�����
            mCamera.setPreviewCallback(pre); //����Ԥ���ص�����
            mCamera.startPreview();//��ʼԤ�����ⲽ��������Ҫ
            //mCamera.autoFocus(mAutoFocusCallback);
            m_setFocusThread.start();
        } 
        catch (IOException exception) 
        {
            mCamera.release();
            mCamera = null;
        }
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		Log.i("jefry", "2222222222222222222222222222222222222222222222222222");
        stopCamera();
      //camera.stopPreview();
        mCamera.setPreviewCallback(null) ;
        mCamera.release();
        mCamera = null;
	}

    /* �Զ���class AutoFocusCallback */
    public final class MyAutoFocusCallback implements android.hardware.Camera.AutoFocusCallback 
    {
        public void onAutoFocus(boolean focused, Camera camera) 
        {
        	Log.w("jefry","111111111111111");
            /* �Ե��������� */
            if (focused && m_bFocus)
            {
            	Log.w("2222222222222","22222222222222");
            	
            }
            m_bFocus = false;
        }
    };

    /* �����ʼ����method */
    private void initCamera() {
        if (mCamera != null) {
            try {
                Camera.Parameters parameters = mCamera.getParameters();
                /*
                 * �趨��Ƭ��СΪ1024*768�� ��ʽΪJPG
                 */
                // parameters.setPictureFormat(PixelFormat.JPEG);
                //parameters.setPictureSize(1024, 768);
                mCamera.setParameters(parameters);
                /* ��Ԥ������ */
                mCamera.startPreview();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /* ֹͣ�����method */
    private void stopCamera() {
        if (mCamera != null) {
            try {
                /* ֹͣԤ�� */
                mCamera.stopPreview();
                
                
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // �������ͷ�Ƿ���ڵ�˽�з���
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA)) {
            // ����ͷ����
            return true;
        } else {
            // ����ͷ������
            return false;
        }
    }

    // ÿ��cam�ɼ�����ͼ��ʱ���õĻص�������ǰ���Ǳ��뿪��Ԥ��
    class priviewCallBack implements Camera.PreviewCallback {

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            // TODO Auto-generated method stub
            // Log.w("wwwwwwwww", data[5] + "");
            // Log.w("֧�ָ�ʽ", mCamera.getParameters().getPreviewFormat()+"");
            //decodeToBitMap(data, camera);
        	synchronized(m_strLock)
        	{
        		mCurrBufferLen = data.length;
        		System.arraycopy(data, 0, mBuffer, 0,mCurrBufferLen);
        	}
        }
    }

    public void decodeToBitMap(byte[] data, Camera _camera) {
        Size size = mCamera.getParameters().getPreviewSize();
        try {
            YuvImage image = new YuvImage(data, ImageFormat.NV21, size.width,
                    size.height, null);
            Log.w("wwwwwwwww", size.width + " " + size.height);
            if (image != null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                image.compressToJpeg(new Rect(0, 0, size.width, size.height),
                        80, stream);
                Bitmap bmp = BitmapFactory.decodeByteArray(
                        stream.toByteArray(), 0, stream.size());
                Log.w("wwwwwwwww", bmp.getWidth() + " " + bmp.getHeight());
                Log.w("wwwwwwwww",
                        (bmp.getPixel(100, 100) & 0xff) + "  "
                                + ((bmp.getPixel(100, 100) >> 8) & 0xff) + "  "
                                + ((bmp.getPixel(100, 100) >> 16) & 0xff));

                stream.close();
            }
        } catch (Exception ex) {
            Log.e("Sys", "Error:" + ex.getMessage());
        }
    }
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

    public void drawImage(byte[] imageBuff,int iTotalLen,int iWidth,int iHeight)
    {
            System.out.println("1111111111len:"+iTotalLen + " width:" + iWidth + " height:" + iHeight  + " len2:" + imageBuff.length);
            
            int[] colors = new int[iWidth * iHeight];
            int k = 0;
            for(int i = 0; i < colors.length; i++)
            {
            	int B = imageBuff[k] & 0x00ff;
            	int G = imageBuff[k+1] & 0x00ff;
            	int R = imageBuff[k+2] & 0x00ff;
            	colors[i] = Color.argb(255,R,G,B);
            	//colors[i]  = ( (imageBuff[k] & 0x00ff) | ((imageBuff[k+1] << 8) & 0x00ff)  | ((imageBuff[k+2]<<16) & 0x00ff) | (255<<24) & 0x00ff );
            	k+=3;
            }
            Bitmap bm = Bitmap.createBitmap(colors, iWidth, iHeight, Config.ARGB_8888);
            /*
            Bitmap bm = Bitmap.createBitmap(iWidth, iHeight, Config.ARGB_8888);
            for(int y=0;y<iHeight;y++)
            {
            	for(int x=0;x<iWidth;x++)
            	{
            		byte [] b = new byte[4]; 
            		b[0] = imageBuff[(y*iHeight + x) * 3];
            		b[1] = imageBuff[(y*iHeight + x) * 3 + 1];
            		b[2] = imageBuff[(y*iHeight + x) * 3 + 2];
            		b[3] = 0;
            		int color = b[3] & 0xFF |  
                				(b[2] & 0xFF) << 8  |  
                				(b[1] & 0xFF) << 16 |  
                				(b[0] & 0xFF) << 24; 
            		bm.setPixel(x, y, color);
            	}
            }*/
            ImageView v = (ImageView)findViewById(R.id.send_image);
            v.setImageBitmap(bm);
    }
    
    public void drawImage(int[] colors,int iWidth,int iHeight)
    {
    	Bitmap bm = Bitmap.createBitmap(colors, iWidth, iHeight, Config.ARGB_8888);
    	ImageView v = (ImageView)findViewById(R.id.send_image);
        v.setImageBitmap(bm);
        zxing(colors, iWidth, iHeight);
    }
    public void showMsg(byte[] pData,int iDataLen)
    {

    }   
    
    public void zxing(int[] colors,int iWidth,int iHeight) 
    		//throws ChecksumException, FormatException
    {
    	/*  	
        Bitmap bMap = Bitmap.createBitmap(mRgba.width(), mRgba.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mRgba, bMap);
        int[] intArray = new int[bMap.getWidth()*bMap.getHeight()];  
        //copy pixel data from the Bitmap into the 'intArray' array  
        bMap.getPixels(intArray, 0, bMap.getWidth(), 0, 0, bMap.getWidth(), bMap.getHeight());  
    	 */
        LuminanceSource source = new RGBLuminanceSource(iWidth, iHeight,colors);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Reader reader = new QRCodeMultiReader();
        String sResult = "";
        try 
        {
        	Result result = reader.decode(bitmap);
        	sResult = result.getText();
        	Log.d("zxing", sResult);
        	TextView v = (TextView)findViewById(R.id.textView2);
    		v.setText(sResult);
    		m_iSleep = 15000;
        }
        catch (NotFoundException e)
        {
        	Log.d("zxing", "Code Not Found");
        	e.printStackTrace();
        } catch (ReaderException e) {
			// TODO Auto-generated catch block
        	Log.d("zxing", "ReaderException");
			e.printStackTrace();
		}
    }
    
	private native String scandFrame(int w,int h,byte[] yuv);
	private native void setJNIEnv(String strTemplatePath);
	static
	{
		System.loadLibrary("ScandImg");
	}
	
}
