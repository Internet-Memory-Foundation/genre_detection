package net.internetmemory.genreclassification;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeVisitor;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.internetmemory.genreclassification.ClassificationWekaModel.pornKeyWordSet;

class ClassificationHTMLFeatures {
	
	private String url;
	private Document htmlParsed;		
	private final Hashtable<String,Double> countTags = new Hashtable<>();
	private Hashtable<String,Double> htmlStatistics = new Hashtable<>();
	private String tokenizedURL;
	private Pattern notLetter = Pattern.compile("\\W+");
	private Pattern yearPattern = Pattern.compile("(199|200|201)\\d{1,5}");

	ClassificationHTMLFeatures(Document doc, String url) throws IOException {
		this.url = url;
		this.htmlParsed = doc;
	}
		
	Hashtable<String,Double> getCountTags(){
		return this.countTags;
	}
	
	Hashtable<String,Double> getHtmlStatistics(){
		return this.htmlStatistics;
	}

	String getTokenizedURL(){
		return this.tokenizedURL;
	}

	/**
	 * compute all HTML features
	 * @throws Exception
	 */
	void computeHTMLFeatures() throws Exception{
		countTagStatistics();
		htmlGlobalTagStatistics();
		tagInHeadStatistics();
		htmlStatisticsDiv();
		htmlStaticsticsParagraphs();
		htmlKeyWords();
		normalizeData();
	}

	/**
	 * count HTML tags represent in a web page
	 */
	void countTagStatistics(){	
		htmlParsed.traverse(new NodeVisitor() {
			double count = 0.;
			String tagName = "";
		    public void head(Node node, int depth) {
		    	if (node instanceof Element) {
		    		tagName = ((Element) node).tagName();
		    		if(countTags.containsKey(tagName)){
		    			count = countTags.get(tagName) + 1.;
		    			countTags.put(tagName, count);
		    		}
		    		else if(tagName.matches("^[a-zA-Z:]+"))
		    				countTags.put(tagName, 1.);
		    	}
		    }
		    public void tail(Node node, int depth) {
		    }
		});	
			
	}
	
	/**
	 * compute overall HTML statistical features
	 */
	private void htmlGlobalTagStatistics() throws MalformedURLException {
		final List<String> anchorLinks = new ArrayList<>();
		final List<String> imgLinks = new ArrayList<>();
		final List<String> anchorImgLinks = new ArrayList<>();
		final List<Integer> countNbImgGif = new ArrayList<>();
		final List<Integer> countNbScriptOutside = new ArrayList<>();
		htmlParsed.traverse(new NodeVisitor(){
			String attributeValue = "";
			public void head(Node node, int depth){
				if (node instanceof Element){
					Element tag = (Element) node;
					if(tag.tagName().equals("a")){
						attributeValue = tag.attr("href");
						if(attributeValue.length() > 0) {
							anchorLinks.add(attributeValue);
						}
					}
					else if(tag.tagName().equals("img")){
						attributeValue = tag.attr("src");
						if(attributeValue.length() > 0){
							imgLinks.add(attributeValue);
							if(attributeValue.endsWith(".gif")){
								countNbImgGif.add(1);
							}
							if(tag.parent().tagName().equals("a")) {
								anchorImgLinks.add(attributeValue);
							}
						}
					}
					else if(tag.tagName().equals("script")){
						attributeValue = tag.attr("src");
						if(attributeValue.length() > 0)
							countNbScriptOutside.add(1);
					}
				}
			}
		    public void tail(Node node, int depth) {
		    }
		});	
		
		int nbYearImgLink = anchorImgLinks.stream()
				.mapToInt(ClassificationGeneralFunctions::countNbOfYears)
				.sum();
				
		int nbYearAnchorLink = 0;
		int nbLinkSameDomain = 0;
		int nbLinkOutside = 0;
		int nbHrefJavascript = 0;
		String domain = new URL(url).getHost().toLowerCase();
		for(String link: anchorLinks){
			link = link.toLowerCase();
			nbYearAnchorLink += ClassificationGeneralFunctions.countNbOfYears(link);
			if(link.startsWith("http")){
				if(link.contains(domain)) {
					nbLinkSameDomain += 1;
				}else {
					nbLinkOutside += 1;
				}
			}
			else{
				if(link.startsWith("javascript")){
					nbHrefJavascript += 1;
				}else {
					nbLinkSameDomain += 1;
				}
			}
		}
		htmlStatistics.put("img", (double) imgLinks.size());
		htmlStatistics.put("img_gif",(double) countNbImgGif.size());
		htmlStatistics.put("script_outside", (double) countNbScriptOutside.size());
		htmlStatistics.put("anchor_with_image", (double) anchorImgLinks.size());
		htmlStatistics.put("year_link_img", (double) nbYearImgLink);
		htmlStatistics.put("year_link_anchor", (double) nbYearAnchorLink);
		htmlStatistics.put("links_same_domains", (double) nbLinkSameDomain);
		htmlStatistics.put("links_to_outside", (double) nbLinkOutside);
		htmlStatistics.put("href_javascript", (double) nbHrefJavascript);
	}
	
	/**
	 * count HTML tags in "head" tag
	 */
	private void tagInHeadStatistics(){
		final List<Integer> countNbLinkHead = new ArrayList<>();
		final List<Integer> countNbCssHead = new ArrayList<>();
		final List<Integer> countNbScriptHead = new ArrayList<>();
		final List<Integer> countNbCssLinkHead = new ArrayList<>();
		Element head = htmlParsed.select("head").first();
		head.traverse(new NodeVisitor(){
			String attributeValue = ""; 
			public void head(Node node, int depth){
				if (node instanceof Element){
					Element tag = (Element) node;
					if(tag.tagName().equals("script"))
						countNbScriptHead.add(1);
					else if(tag.tagName().equals("style"))
						countNbCssHead.add(1);
					else if(tag.tagName().equals("link")){
						countNbLinkHead.add(1);
						attributeValue = tag.attr("rel");
						if(attributeValue.equals("stylesheet"))
							countNbCssLinkHead.add(1);
					}
				}
			}
			public void tail(Node node, int depth) {
		    }
		});
		htmlStatistics.put("link_head",(double) countNbLinkHead.size());
		htmlStatistics.put("css_code_head", (double) countNbCssHead.size());
		htmlStatistics.put("script_tag_head", (double) countNbScriptHead.size());
		htmlStatistics.put("link_css_head", (double) countNbCssLinkHead.size());
	}
	
	/**
	 * compute statistical features for HTML tag "div"
	 * @throws Exception
	 */
	private void htmlStatisticsDiv() throws Exception{
		Elements selectedDiv = new Elements(selectDiv());
		Map<String, Boolean> option = new HashMap<>();
		option.put("currency", true);
		option.put("table", true);
		option.put("list", true);
		option.put("p", true);
		textStatisticsElements(selectedDiv,"div", option);
		computeElementstatistics(selectedDiv,"div", option);
	}
	
	/**
	 * Return a list of smallest division
	 * @return
	 */
	private List<Element> selectDiv(){
		Elements allDiv = htmlParsed.select("div");
		return allDiv.stream().filter(div -> div.children().select("div").isEmpty()).collect(Collectors.toList());
	}
	
	/**
	 * compute statistical features from list of HTML tags 
	 * @param eles List of HTML tags
	 * @param nameElement Name of HTML tag
	 * @param option Compute specific features or not
	 * @throws Exception
	 */
	private void computeElementstatistics(Elements eles, String nameElement, Map<String,Boolean> option) throws Exception{	
		if(eles.size() == 0)
			return;
		double nbAnchor, nbList, nbTable, nbImg, nbImgAnchor, nbPTag, nbDescendants;		
		List<Double> nbAnchorEle = new ArrayList<>();
		List<Double> nbListEle = new ArrayList<>();
		List<Double> nbTableEle = new ArrayList<>();
		List<Double> nbImgEle = new ArrayList<>();
		List<Double> nbImgAnchorEle = new ArrayList<>();
		List<Double> nbPTagEle = new ArrayList<>();
		List<Double> nbDescendantsEle = new ArrayList<>();
		for(Element div : eles){
			nbAnchor = 0;
			nbList = 0;
			nbTable = 0;
			nbImg = 0;
			nbImgAnchor = 0;
			nbPTag = 0;
			nbDescendants = 0;
			for(Element tag : div.getAllElements()){
				nbDescendants += 1;
				if(tag.tagName().equals("a"))
					nbAnchor += 1;
				else if(option.containsKey("list") && option.get("list") && (tag.tagName().equals("ul") || tag.tagName().equals("li")))
					nbList += 1;
				else if(option.containsKey("table") && option.get("table") &&
						(tag.tagName().equals("table") || tag.tagName().equals("tr") || tag.tagName().equals("td")))
					nbTable += 1;
				else if(tag.tagName().equals("img")){
					if(tag.parent().tagName().equals("a"))
						nbImgAnchor += 1;
					else
						nbImg += 1;
				}
				else if(option.containsKey("p") && option.get("p") && (tag.tagName().equals("p")))
					nbPTag += 1;
			}
			nbAnchorEle.add(nbAnchor);			
			nbListEle.add(nbList);
			nbTableEle.add(nbTable);
			nbImgEle.add(nbImg);
			nbImgAnchorEle.add(nbImgAnchor);
			nbPTagEle.add(nbPTag);
			nbDescendantsEle.add(nbDescendants);
		}
		Hashtable<String, List<Double>> eleStat = new Hashtable<String, List<Double>>();
		eleStat.put("anchor_by_" + nameElement, nbAnchorEle);
		if(option.containsKey("list") && option.get("list"))
			eleStat.put("list_by_" + nameElement, nbListEle);
		if(option.containsKey("table") && option.get("table"))
			eleStat.put("table_by_" + nameElement, nbTableEle);
		eleStat.put("img_by_" + nameElement, nbImgEle);
		eleStat.put("img_anchor_by_" + nameElement, nbImgAnchorEle);
		if(option.containsKey("p") && option.get("p"))
			eleStat.put("tag_p_by_" + nameElement, nbPTagEle);
		eleStat.put("descendants_by_" + nameElement, nbDescendantsEle);
		List<String> averageName = Arrays.asList("__mean","__std","__2nd_skew_coef");
		List<String> bigName = Arrays.asList("__highest","__2nd_highest","__3rd_highest");
		for(String name : eleStat.keySet()){
			List<Double> average = ClassificationGeneralFunctions.getAverageSkewness(eleStat.get(name));
			List<Double> highestElements = ClassificationGeneralFunctions.getHighestElementStatistics(eleStat.get(name), average.get(0));			
			for(int i = 0; i < averageName.size(); i++){
				htmlStatistics.put(name + averageName.get(i), average.get(i));
			}
			for(int i = 0; i < bigName.size(); i++){
				htmlStatistics.put(name + bigName.get(i), highestElements.get(i));
			}
		}
	}
	
	/**
	 * Compute statistical text features for list of HTML tags
	 * @param eles List of HTML tags
	 * @param nameElement Name of HTML tag
	 * @param option Compute specific features or not
	 * @throws Exception
	 */
	private void textStatisticsElements(Elements eles, String nameElement, Map<String, Boolean> option) throws Exception{
		if(eles.size() == 0)
			return;
		List<String> textOfElements = new ArrayList<>();
		for(Element el:eles){
			if(el.select("script").isEmpty()){
				String text = el.text().replaceAll("<!--.*-->|\r|\n", " ")
						.replaceAll("\\s+|&nbsp;", " ");
				textOfElements.add(text);
			}
		}
		List<Double> nbCharsInElement = new ArrayList<>();
		List<Double> nbNumberInElement = new ArrayList<>();
		List<Double> nbYearInElement= new ArrayList<>();
		List<Double> nbCurrencyInElement= new ArrayList<>();
		for(String text : textOfElements){
			if(text.length() > 0){
				int textLength = text.replaceAll("\\s+", "").length();
				nbCharsInElement.add((double) textLength);
				double countNumber = (double) ClassificationGeneralFunctions.countNbOfNumbers(text);
				if(countNumber > 0){
					nbNumberInElement.add(countNumber / textLength);
				}
				double countOccurency = (double) ClassificationGeneralFunctions.countNbOfYears(text);
				if(countOccurency > 0){
					nbYearInElement.add(countOccurency);
				}
				if(option.containsKey("currency") && option.get("currency")){
					countOccurency = (double) ClassificationGeneralFunctions.countNbOfCurrency(text);
					if(countOccurency > 0){
						nbCurrencyInElement.add(countOccurency);
					}
					
				}
			}
		}		
		Hashtable<String, List<Double>> elementStat = new Hashtable<>();
		elementStat.put("chars_by_" + nameElement, nbCharsInElement);
		elementStat.put("number_in_" + nameElement, nbNumberInElement);
		elementStat.put("year_in_" + nameElement, nbYearInElement);
		if(option.containsKey("currency") && option.get("currency"))
			elementStat.put("currency_in_" + nameElement, nbCurrencyInElement);
		List<String> averageName = Arrays.asList("__mean","__std","__2nd_skew_coef");
		List<String> bigName = Arrays.asList("__highest","__2nd_highest","__3rd_highest");
		for(String name : elementStat.keySet()){
			if(!name.startsWith("currency")) {
				List<Double> average = ClassificationGeneralFunctions.getAverageSkewness(elementStat.get(name));
				List<Double> highestElements = ClassificationGeneralFunctions.getHighestElementStatistics(elementStat.get(name), average.get(0));
				for (int i = 0; i < averageName.size(); i++) {
					htmlStatistics.put(name + averageName.get(i), average.get(i));
				}
				for (int i = 0; i < bigName.size(); i++) {
					htmlStatistics.put(name + bigName.get(i), highestElements.get(i));
				}
				htmlStatistics.put(name + "__percent", (double) (elementStat.get(name).size() / eles.size()));
			}else{
				htmlStatistics.put(name, (double) nbCurrencyInElement.size());
				htmlStatistics.put(name + "__percent", ((double) nbCurrencyInElement.size() / eles.size()));
			}
		}
	}
	
	/**
	 * compute statistical features for HTML tag "p"	
	 * @throws Exception
	 */
	private void htmlStaticsticsParagraphs() throws Exception{
		Elements listP = htmlParsed.select("p");
		Map<String,Boolean> option = new HashMap<>();
		option.put("currency", true);
		option.put("table", true);
		option.put("list", true);
		option.put("p", true);
		textStatisticsElements(listP, "p", option);
		computeElementstatistics(listP, "p", option);
	}

	private void htmlKeyWords(){
		htmlStatistics.put("blog_in_url", url.toLowerCase().contains("blog") ? 1. : 0.);
		htmlStatistics.put("news_in_url", url.toLowerCase().contains("news") ? 1. : 0.);
		htmlStatistics.put("forum_in_url", url.toLowerCase().contains("forum") ? 1. : 0.);
		htmlStatistics.put("thread_in_url", url.toLowerCase().contains("thread") ? 1. : 0.);
		htmlStatistics.put("date_in_url", yearPattern.matcher(url.toLowerCase()).find() ? 1. : 0.);
		int numPornKeywordInURL = 0;
		for(String pornKeyword: pornKeyWordSet){
			numPornKeywordInURL += StringUtils.countMatches(url.toLowerCase(), pornKeyword);
		}
		htmlStatistics.put("porn_keyword_in_url", (double)numPornKeywordInURL);

		int numPornKeywordInTitle = 0;
		String[] titleTokens = htmlParsed.getElementsByTag("title").text().toLowerCase().replaceAll("\\p{P}", " ").split("\\s+");
		for(String pornKeyword: pornKeyWordSet){
			for(String token: titleTokens) {
				if(token.equals(pornKeyword)){
					numPornKeywordInTitle += 1;
				}
			}
		}
		htmlStatistics.put("porn_keyword_in_title", (double)numPornKeywordInTitle);

		String fullText = htmlParsed.text();
		int numPornKeyWord = 0;
		Set<String> foundKeyword = new HashSet<>();
		String[] tokens = fullText.toLowerCase().replaceAll("\\p{P}", " ").split("\\s+");
		for(String pornKeyWord: pornKeyWordSet){
			for(String token: tokens){
				numPornKeyWord += token.equals(pornKeyWord) ? 1 : 0;
				if(token.equals(pornKeyWord)){
					foundKeyword.add(pornKeyWord);
				}
			}
		}
		//foundKeyword.forEach(System.out::println);
		htmlStatistics.put("num_porn_keyword", (double)numPornKeyWord);
	}
	

	/**
	 * Normalize features
	 */
	private void normalizeData(){
		double nbAllTags = 0.;
		for(Double val : countTags.values())
			nbAllTags += val;
		for(String key : this.countTags.keySet())
			htmlStatistics.put(key,countTags.get(key) / nbAllTags);
		if(countTags.contains("a") && countTags.get("a") > 0){
			htmlStatistics.put("anchor_with_image", htmlStatistics.get("anchor_with_image") / countTags.get("a"));
			htmlStatistics.put("year_link_anchor", htmlStatistics.get("year_link_anchor") / countTags.get("a"));
			htmlStatistics.put("links_same_domains", htmlStatistics.get("links_same_domains") / countTags.get("a"));
		}
		if(countTags.contains("script") && countTags.get("script") > 0){
			htmlStatistics.put("href_javascript", htmlStatistics.get("href_javascript") / countTags.get("script"));
			htmlStatistics.put("script_outside", htmlStatistics.get("script_outside") / countTags.get("script"));
			htmlStatistics.put("script_tag_head", htmlStatistics.get("script_tag_head") / countTags.get("script"));
			htmlStatistics.put("css_code_head", htmlStatistics.get("css_code_head") / countTags.get("script"));
		}
		if(countTags.contains("img") && countTags.get("img") > 0){
			htmlStatistics.put("year_link_img", htmlStatistics.get("year_link_img") / countTags.get("img"));
			htmlStatistics.put("img_gif", htmlStatistics.get("img_gif") / countTags.get("img"));		
		}
		if(countTags.contains("link") && countTags.get("link") > 0)
			htmlStatistics.put("link_css_head", htmlStatistics.get("link_css_head") / countTags.get("link"));
		
	}
	
}
