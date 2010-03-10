<%@page contentType="text/html" pageEncoding="UTF-8" isELIgnored="false"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Admin page</title>
    </head>
    <body>
        <p>Hello, ${pageContext.request.userPrincipal.name}! You can <a href="${logoutURL}">sign out</a>.</p>
        <h1>Cache statistics</h1>
        <p><b>Cache hits:</b> ${cacheStats.cacheHits}</p>
        <p><b>Cache misses:</b> ${cacheStats.cacheMisses}</p>
        <p><b>Size of cache:</b> ${cacheStats.objectCount}</p>
        <form action="clearImageCache">
            <button type="submit" onclick="return confirm('Are you sure you want to clear the cache?');">Clear cache</button>
        </form>

        <h1>Image store</h1>
        <form action="loadImageTiles">
            <input type="text" name="imagename" value="bluemarble"/>
            <button type="submit" onclick="return confirm('Are you sure you want to load image tiles?');">Load image tiles</button>
        </form>
    </body>
</html>
