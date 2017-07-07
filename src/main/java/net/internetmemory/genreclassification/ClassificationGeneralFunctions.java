package net.internetmemory.genreclassification;

import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class ClassificationGeneralFunctions {
	private static Set<String> prices = ClassificationWekaModel.currencies;
	private static double mean(List<Double> m) {
	    double sum = 0.0;
	    for (double el : m) {
	        sum += el;
	    }
	    return sum / m.size();
	}
	
	private static double median(List<Double> m) {
	    int middle = m.size()/2;
	    if (m.size()%2 == 1) {
	        return m.get(middle);
	    } else {
	        return (m.get(middle-1) + m.get(middle)) / 2.0;
	    }
	}
	
	private static double standardDeviation(List<Double> m){
		double average = mean(m);
		double variance = 0.0;
		for(double el : m){
			variance += (el - average) * (el - average);
		}
		variance = variance / m.size();
		return Math.sqrt(variance);
	}
	
	static int countNbOfNumbers(String text){
		int nbCount = 0;
		for(char ch: text.toCharArray()){
			if(CharUtils.isAsciiNumeric(ch)){
				nbCount++;
			}
		}
		return nbCount;
	}
	
	static int countNbOfYears(String text){
		int res = 0;
		Pattern pattern = Pattern.compile("(199|200|201)\\d");
		Matcher matcher = pattern.matcher(text);
		while(matcher.find()){
			res += 1;
		}
		return res;
	}

	private static String getPriceSymbol(String text){
		String replaced = text.toLowerCase();
		for(String symbol: prices){
			if(StringUtils.countMatches(replaced, symbol) == 1){
				List<String> parts = Arrays.stream(replaced.split(Pattern.quote(symbol)))
						.filter(s -> !s.equals("")).collect(Collectors.toList());
				for(int i=0; i<parts.size(); i++){
					parts.set(i, parts.get(i).replaceAll("[,. \"\\u00a0*]", ""));
				}
				if(StringUtils.join(parts, "").replaceAll("\\d+", "").length() > 15){
					return null;
				}
				char[] before;
				char[] after;
				if(replaced.equals(symbol)){
					return null;
				}else {
					if(parts.size() == 2){
						before = parts.get(0).toCharArray();
						after = parts.get(1).toCharArray();
					}else{
						if(replaced.startsWith(symbol)){
							before = new char[0];
							after = parts.get(0).toCharArray();
						}else{
							before = parts.get(0).toCharArray();
							after = new char[0];
						}
					}
				}
				int countNonDigit = 0;
				Boolean[] digitAround = {true, true};
				for(int i = before.length-1; i > -1; i--){
					if(!CharUtils.isAsciiNumeric(before[before.length-1])){
						digitAround[0] = false;
					}
					if(!CharUtils.isAsciiNumeric(before[i]) || countNonDigit != 0){
						countNonDigit++;
					}
					if(countNonDigit > 5){
						break;
					}
				}
				if(countNonDigit > 5){
					continue;
				}
				countNonDigit = 0;
				for (char anAfter : after) {
					if(!CharUtils.isAsciiNumeric(after[0])){
						digitAround[1] = false;
					}
					if (!CharUtils.isAsciiNumeric(anAfter) || countNonDigit != 0) {
						countNonDigit++;
					}
					if (countNonDigit > 5) {
						break;
					}
				}
				if(countNonDigit > 5){
					continue;
				}
				if(!(parts.size() == 2 && (digitAround[0] || digitAround[1]) ||
						parts.size() == 1 && digitAround[0] && digitAround[1])){
					continue;
				}
				return symbol;
			}
		}
		return null;
	}
	
	static int countNbOfCurrency(String text){
		int res = 0;
		if(getPriceSymbol(text) != null){
			res++;
		}
		return res;
	}	

	static List<Double> getAverageSkewness(List<Double> li){
		li.removeIf(ele -> ele.equals(0.));
		List<Double> res = new ArrayList<>();
		if(li.size() > 0){
			res.add(mean(li));
			res.add(standardDeviation(li));
			if(res.get(1) > 0){
				res.add((res.get(0) - median(li)) / res.get(1));
			}
			else
				res.add(0.);
		}
		else{
			res.add(0.);
			res.add(0.);
			res.add(0.);
		}				
		return res;
	}

	static List<Double> getHighestElementStatistics(List<Double> li, double meanValue){
		Collections.sort(li);
		int len = li.size();
		List<Double> res = new ArrayList<>();
		if(len > 0){
			res.add(li.get(len-1) / meanValue);
		}
		if(len > 1){
			res.add(li.get(len-2) / meanValue);
		}
		if(len > 2){
			res.add(li.get(len-3) / meanValue);
		}
		int resSize = res.size();
		if(resSize < 3){
			for(int i = 0; i < 3 - resSize; i++){
				res.add(0.);
			}			
		}
 		return res;
	}
	
}
