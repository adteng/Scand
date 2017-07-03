#include "MarkingImg.h"

typedef struct _NumberElement
{
//	IplImage *plate_number;
	int x;
	int y;
	int w;
	int h;
	string strWord;
} NumberElement;

CALL_BACK_SHOW_IMAGE_FUN pShowImage;
CALL_BACK_SHOW_MSG_FUN pShowMsg;

void setShowImgFun(CALL_BACK_SHOW_IMAGE_FUN f)
{
	pShowImage = f;
}
void setShowMsgFun(CALL_BACK_SHOW_MSG_FUN f)
{
	pShowMsg = f;
}

bool cmp(NumberElement a,NumberElement b)
{
	return a.x < b.x;
}

typedef struct _TempNumber
{
	string strNumber;
	Mat m;
}TempNumber;

vector<TempNumber> m_vt;

Mat Operater(Mat &gray);

string MarkingImg(int width,int height,uchar *_yuv,const char *dir)
{
	string strValues = "";
    Mat myuv(height+height/2, width, CV_8UC1,_yuv);
    Mat mbgr(height, width, CV_8UC3, cv::Scalar(0,0,255));
    cvtColor(myuv, mbgr, CV_YUV420sp2BGR);
    
    Mat t,oriMat;
 	transpose(mbgr,t);//转90度
 	flip(t,oriMat,1);
 	
 	Mat w_mat = oriMat.clone();
 	Mat gray;
	cvtColor(w_mat,gray,CV_BGR2GRAY);
	
	Mat gray_bi = Operater(gray);
	vector<vector<Point> > contours;
	vector<Vec4i> hierarchy;
	findContours( gray_bi, contours, hierarchy, 
	  CV_RETR_EXTERNAL,//CV_RETR_TREE,CV_RETR_LIST,CV_RETR_CCOMP,CV_RETR_EXTERNAL
	  CV_CHAIN_APPROX_SIMPLE, 
	  Point(0, 0) );
	 
	int i=0;
	string str = "";
	vector<vector<Point> >::iterator itc= contours.begin();
	while (itc!=contours.end()) 
	{	 
		double tmparea = fabs(contourArea(*itc));//面积
		double contLenth =  arcLength(*itc,true);//周长
		double Afa = (4 * CV_PI *  tmparea)/(contLenth * contLenth);//与圆的近似度
		
		RotatedRect minRect = minAreaRect(*itc);  
		Point2f vertices[4];  
		minRect.points(vertices); //获得最小外接矩形4个点
		double L1 = sqrt((vertices[0].x-vertices[1].x) * (vertices[0].x-vertices[1].x) + (vertices[0].y-vertices[1].y) * (vertices[0].y-vertices[1].y));
		double L2 = sqrt((vertices[2].x-vertices[1].x) * (vertices[2].x-vertices[1].x) + (vertices[2].y-vertices[1].y) * (vertices[2].y-vertices[1].y));
		float angle;
		if(L1 > L2) 
		{
			int T = L2;
			L2 = L1;
			L1 = T;
			angle = atan2((vertices[0].y-vertices[1].y),(vertices[0].x-vertices[1].x)) * 180.0/CV_PI;
		}
		else
			angle = atan2((vertices[2].y-vertices[1].y),(vertices[2].x-vertices[1].x)) * 180.0/CV_PI;
		
		//最小外接圆
		Point2f center;//圆心  
		float radius;//半径  
		minEnclosingCircle(*itc, center, radius);
		
		Rect rt = boundingRect(*itc);//包含轮廓的矩形
		double l = sqrt((center.x - gray_bi.size().width/2) * (center.x - gray_bi.size().width/2) + (center.y - gray_bi.size().height/2) * (center.y - gray_bi.size().height/2));
		if(l > 20)
		{
			itc = contours.erase(itc);
		}
		else if(L1/L2 < 0.8)
			itc = contours.erase(itc);
		else if(L2 < gray_bi.size().width/20.0)
			itc = contours.erase(itc);
		else
		{
			char sTmp[64] = {0};
			sprintf(sTmp,"%d,%d,%d,%d,",rt.x,rt.y,rt.x + rt.width,rt.y + rt.height);
			str += sTmp;
			i++;
			itc++;
			Mat image_roi = oriMat(rt).clone();
			(*pShowImage)(image_roi.data,image_roi.step[0]*image_roi.rows,image_roi.cols,image_roi.rows);
			break;
		}
	}
	char *pRetBuffer = new char[str.length() + 4];
	memset(pRetBuffer,0,str.length() + 3);
	sprintf(pRetBuffer,"%d,%s",i,str.c_str());
	str = pRetBuffer;
	delete [] pRetBuffer;
	return str;
}

Mat Operater(Mat &gray)
{
	//高斯滤波器滤波去噪（可选）
	int ksize = 3;
	Mat g_gray;
	Mat G_kernel = getGaussianKernel(ksize,0.3*((ksize-1)*0.5-1)+0.8);
	filter2D(gray,g_gray,-1,G_kernel);

/*	//Sobel算子（x方向和y方向）
	Mat sobel_x,sobel_y;
	Sobel(g_gray,sobel_x,CV_16S,1,0,3);
	Sobel(g_gray,sobel_y,CV_16S,0,1,3); 
	Mat abs_x,abs_y;
	convertScaleAbs(sobel_x,abs_x);
	convertScaleAbs(sobel_y,abs_y);
	Mat grad;
	addWeighted(abs_x,0.5,abs_y,0.5,0,grad);
	*/
	Mat detected_edges;
	blur( g_gray, detected_edges, Size(3,3) );
	/// 运行Canny算子
	Canny( detected_edges, detected_edges, 80, 80*2.5, 3 );
	/// 使用 Canny算子输出边缘作为掩码显示原图像
	/*
	dst = Scalar::all(0);
	src.copyTo( dst, detected_edges);
	imshow( window_name, dst );
	*/
	//return detected_edges;
	Mat img_bin;
	threshold(detected_edges,img_bin,0,255,CV_THRESH_BINARY |CV_THRESH_OTSU);
    Mat elementX = getStructuringElement(MORPH_RECT, Size(3, 3),Point(-1,-1));
    Mat m_ResImg;
    dilate(img_bin, m_ResImg,elementX,Point(-1,-1),2);
	//erode(m_ResImg, m_ResImg,elementX,Point(-1,-1),1);
	//dilate(m_ResImg, m_ResImg,elementX,Point(-1,-1),1);	
	return m_ResImg;
}

