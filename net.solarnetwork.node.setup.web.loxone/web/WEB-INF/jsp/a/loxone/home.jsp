<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="setup" uri="http://solarnetwork.net/taglib/setup" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

<div class="loxone-heading">
	<div class="loxone-upload-file" onclick="Loxone.controlView.openFileInput()">Upload</div>

	<div class="title">Device configuration</div>

	<setup:url value="/a/loxone/{configId}/sources" var="uploadSourcesUrl">
		<spring:param name="configId" value="${configId}" />
	</setup:url>

	<form id="loxone-file-upload-form" action="${uploadSourcesUrl}"  method="post" onchange="" enctype="multipart/form-data" hidden>
		<input id="loxone-file-input" type="file" name="file" onchange="Loxone.controlView.submitFile()">
		<sec:csrfInput/>
	</form>

  <input id="filter-input" type="text" placeholder="filter" onkeyup="Loxone.controlView.draw()">
  <div class="table-heading">
    <div class="loxone-name-column heading-column" onclick="Loxone.controlView.sortColumn('name')">
      <span class="name">Name</span>
      <div id="sort-icon-name" class="sort-icon ascending"></div>
    </div>
    <div class="loxone-source-column heading-column" onclick="Loxone.controlView.sortColumn('source')">
      <span class="name">Source ID</span>
      <div id="sort-icon-source" class="sort-icon ascending"></div>
    </div>
    <div class="loxone-type-column heading-column" onclick="Loxone.controlView.sortColumn('type')">
      <span class="name">Type</span>
      <div id="sort-icon-type" class="sort-icon"></div>
    </div>
    <div class="loxone-category-column heading-column" onclick="Loxone.controlView.sortColumn('cat')">
      <span class="name">Category</span>
      <div id="sort-icon-cat" class="sort-icon"></div>
    </div>
    <div class="loxone-room-column heading-column" onclick="Loxone.controlView.sortColumn('room')">
      <span class="name">Room</span>
      <div id="sort-icon-room" class="sort-icon"></div>
    </div>
    <div class="loxone-enable-column heading-column" >
      <span id="loxone-enable-text" class="name" onclick="Loxone.controlView.openEnableFilter()">All</span>
			<div id="loxone-enable-filter" class="enable-filter-dropdown" tabindex="-1">
				<div class="enable-filter" onclick="Loxone.controlView.enableFilter('All')">All</div>
				<div class="enable-filter" onclick="Loxone.controlView.enableFilter('Enabled')">Enabled</div>
				<div class="enable-filter" onclick="Loxone.controlView.enableFilter('Disabled')">Disabled</div>
			</div>
    </div>
    <div class="loxone-frequency-column heading-column">
      <span class="name">Frequency</span>
    </div>
  </div>
</div>

<div id="control-view" class="loxone-list"></div>

<script type="application/javascript" src="<setup:url value='/js/loxone-view-control.js'/>"></script>
<script type="application/javascript" src="<setup:url value='/js/loxone-websocket.js'/>"></script>
