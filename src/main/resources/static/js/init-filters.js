// Update available filter operations based on field type
function updateFilterOptions(select) {
    const field = select.value;
    const operationSelect = select.nextElementSibling;
    const fieldTypesElement = document.getElementById("fieldTypesData");
    if (!fieldTypesElement || !fieldTypesElement.dataset.fieldTypes) {
        return;
    }
    const fieldTypes = JSON.parse(fieldTypesElement.dataset.fieldTypes);
    const fieldType = fieldTypes[field] || "String";
    const currentOperation = operationSelect.getAttribute("data-operation") || operationSelect.value || "equals";
    operationSelect.innerHTML = "";

    let operators = [];
    if (fieldType === "String") {
        operators = [
            {value: "equals", text: "Equals"},
            {value: "not equal", text: "Not Equal"},
            {value: "contains", text: "Contains"},
            {value: "starts with", text: "Starts With"},
            {value: "ends with", text: "Ends With"},
            {value: "is null", text: "Is Null"},
            {value: "is not null", text: "Is Not Null"}
        ];
    } else if (["Long", "Integer", "Double", "BigDecimal", "Float", "Short"].includes(fieldType)) {
        operators = [
            {value: "equals", text: "Equals"},
            {value: "not equal", text: "Not Equal"},
            {value: "greater than", text: "Greater Than"},
            {value: "less than", text: "Less Than"},
            {value: "greater than or equal", text: "Greater Than or Equal"},
            {value: "less than or equal", text: "Less Than or Equal"},
            {value: "is null", text: "Is Null"},
            {value: "is not null", text: "Is Not Null"}
        ];
    } else if (fieldType === "Boolean") {
        operators = [
            {value: "true", text: "True"},
            {value: "false", text: "False"},
            {value: "is null", text: "Is Null"},
            {value: "is not null", text: "Is Not Null"}
        ];
    } else if (["LocalTime", "OffsetTime", "LocalDateTime", "OffsetDateTime", "Instant", "ZonedDateTime"].includes(fieldType)) {
        operators = [
            {value: "equals", text: "Equals"},
            {value: "not equal", text: "Not Equal"},
            {value: "greater than", text: "Greater Than"},
            {value: "less than", text: "Less Than"},
            {value: "greater than or equal", text: "Greater Than or Equal"},
            {value: "less than or equal", text: "Less Than or Equal"},
            {value: "is null", text: "Is Null"},
            {value: "is not null", text: "Is Not Null"}
        ];
    } else if (fieldType === "byte[]" || fieldType === "UUID") {
        operators = [
            {value: "equals", text: "Equals"},
            {value: "not equal", text: "Not Equal"},
            {value: "is null", text: "Is Null"},
            {value: "is not null", text: "Is Not Null"}
        ];
    }

    operators.forEach(op => {
        const option = document.createElement("option");
        option.value = op.value;
        option.textContent = op.text;
        if (op.value === currentOperation) {
            option.selected = true;
        }
        operationSelect.appendChild(option);
    });

    operationSelect.setAttribute("data-operation", currentOperation);
    updateFilterControls(select, operationSelect);
}

function updateFilterControls(fieldSelect, operationSelect) {
    const field = fieldSelect.value;
    const fieldTypesElement = document.getElementById("fieldTypesData");
    if (!fieldTypesElement || !fieldTypesElement.dataset.fieldTypes) {
        return;
    }
    const fieldTypes = JSON.parse(fieldTypesElement.dataset.fieldTypes);
    const fieldType = fieldTypes[field] || "String";

    const operation = operationSelect.value;
    const valueContainer = operationSelect.nextElementSibling;
    const valueInput = valueContainer.querySelector("input[name='filterValue']");
    const calendarIcon = valueContainer.querySelector(".calendar-icon");
    const timezoneInput = valueContainer.querySelector("input[name='timezoneValue']");
    const allowNullLabel = operationSelect.parentElement.querySelector("label.allow-null");
    const emptyStringCheckbox = operationSelect.parentElement.querySelector("input[name='treatEmptyAsEmptyString']");
    const isEmptyStringChecked = emptyStringCheckbox && emptyStringCheckbox.checked;

    if (valueInput.flatpickrInstance) {
        valueInput.flatpickrInstance.destroy();
        valueInput.flatpickrInstance = null;
    }

    if (fieldType === "String" && isEmptyStringChecked) {
        operationSelect.value = "equals";
        valueInput.value = "";
        operationSelect.style.display = "inline-block";
        valueContainer.style.display = "none";
        if (allowNullLabel) allowNullLabel.style.display = "none";
    } else {
        operationSelect.style.display = "inline-block";
        if (["true", "false", "is null", "is not null"].includes(operation)) {
            valueContainer.style.display = "none";
            calendarIcon.style.display = "none";
            if (timezoneInput) {
                timezoneInput.style.display = "none";
                timezoneInput.value = "";
            }
        } else if (["LocalTime", "OffsetTime", "LocalDateTime", "OffsetDateTime", "Instant", "ZonedDateTime"].includes(fieldType)) {
            valueContainer.style.display = "inline-block";
            calendarIcon.style.display = "inline-block";
            if (timezoneInput) {
                if (fieldType === "OffsetTime" || fieldType === "OffsetDateTime" || fieldType === "Instant" || fieldType === "ZonedDateTime") {
                    timezoneInput.style.display = "inline-block";
                } else {
                    timezoneInput.style.display = "none";
                    timezoneInput.value = "";
                    if (valueInput.value) {
                        valueInput.value = valueInput.value.includes("+") ? valueInput.value.split("+")[0] :
                            valueInput.value.includes("-") ? valueInput.value.split("-")[0] :
                                valueInput.value.includes("Z") ? valueInput.value.replace("Z", "") :
                                    valueInput.value;
                    }
                }
            }

            let internalFormat, displayFormat;
            if (fieldType === "LocalTime") {
                internalFormat = "H:i:S";
                displayFormat = "H:i:S";
            } else if (fieldType === "OffsetTime") {
                internalFormat = "H:i:S";
                displayFormat = "H:i:S";
            } else if (fieldType === "LocalDateTime") {
                internalFormat = "Y-m-d H:i:S";
                displayFormat = "Y-m-d H:i:S";
            } else if (fieldType === "OffsetDateTime" || fieldType === "Instant" || fieldType === "ZonedDateTime") {
                internalFormat = "Y-m-d H:i:S";
                displayFormat = "Y-m-d H:i:S";
            }

            valueInput.dataset.displayFormat = fieldType;

            const config = {
                enableTime: true,
                noCalendar: fieldType === "LocalTime" || fieldType === "OffsetTime",
                dateFormat: internalFormat,
                time_24hr: true,
                allowInput: true,
                position: "below",
                parseDate: (dateStr, formatStr) => {
                    try {
                        const parts = dateStr.split(" ");
                        if (formatStr === "H:i:S") {
                            const today = new Date();
                            const [hours, minutes, seconds] = dateStr.split(":");
                            today.setHours(parseInt(hours, 10), parseInt(minutes, 10), parseInt(seconds, 10), 0);
                            return today;
                        } else if (formatStr === "Y-m-d H:i:S") {
                            if (parts.length === 2) {
                                const [datePart, timePart] = parts;
                                const [year, month, day] = datePart.split("-").map(Number);
                                const [hours, minutes, seconds] = timePart.split(":").map(Number);
                                return new Date(year, month - 1, day, hours, minutes, seconds);
                            } else if (parts.length === 1 && dateStr.includes("-")) {
                                const [year, month, day] = dateStr.split("-").map(Number);
                                return new Date(year, month - 1, day, 0, 0, 0);
                            }
                        }
                        return new Date(dateStr);
                    } catch (e) {
                        return null;
                    }
                },
                onChange: function(selectedDates, dateStr, instance) {
                    const dateObj = selectedDates[0];
                    if (dateObj) {
                        const year = dateObj.getFullYear();
                        const month = (dateObj.getMonth() + 1).toString().padStart(2, "0");
                        const day = dateObj.getDate().toString().padStart(2, "0");
                        const hours = dateObj.getHours().toString().padStart(2, "0");
                        const minutes = dateObj.getMinutes().toString().padStart(2, "0");
                        const seconds = dateObj.getSeconds().toString().padStart(2, "0");

                        let formattedValue;
                        if (fieldType === "LocalTime" || fieldType === "OffsetTime") {
                            formattedValue = `${hours}:${minutes}:${seconds}`;
                        } else if (fieldType === "LocalDateTime") {
                            formattedValue = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
                        } else if (fieldType === "OffsetDateTime" || fieldType === "Instant" || fieldType === "ZonedDateTime") {
                            formattedValue = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
                        }
                        valueInput.value = formattedValue;
                        if (timezoneInput && timezoneInput.value && (fieldType === "OffsetTime" || fieldType === "OffsetDateTime" || fieldType === "Instant" || fieldType === "ZonedDateTime")) {
                            valueInput.value += timezoneInput.value;
                        }
                    }
                },
                onClose: function(selectedDates, dateStr, instance) {
                    const dateObj = selectedDates[0];
                    const inputValue = valueInput.value;

                    if (dateObj) {
                        const year = dateObj.getFullYear();
                        const month = (dateObj.getMonth() + 1).toString().padStart(2, "0");
                        const day = dateObj.getDate().toString().padStart(2, "0");
                        const hours = dateObj.getHours().toString().padStart(2, "0");
                        const minutes = dateObj.getMinutes().toString().padStart(2, "0");
                        const seconds = dateObj.getSeconds().toString().padStart(2, "0");

                        let formattedValue;
                        if (fieldType === "LocalTime" || fieldType === "OffsetTime") {
                            formattedValue = `${hours}:${minutes}:${seconds}`;
                        } else if (fieldType === "LocalDateTime") {
                            formattedValue = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
                        } else if (fieldType === "OffsetDateTime" || fieldType === "Instant" || fieldType === "ZonedDateTime") {
                            formattedValue = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
                        }
                        valueInput.value = formattedValue;
                        if (timezoneInput && timezoneInput.value && (fieldType === "OffsetTime" || fieldType === "OffsetDateTime" || fieldType === "Instant" || fieldType === "ZonedDateTime")) {
                            valueInput.value += timezoneInput.value;
                        }
                    } else if (inputValue) {
                        try {
                            const cleanInputValue = inputValue.includes("+") ? inputValue.split("+")[0] :
                                inputValue.includes("-") ? inputValue.split("-")[0] :
                                    inputValue.includes("Z") ? inputValue.replace("Z", "") : inputValue;
                            const parsedDate = new Date(cleanInputValue);
                            if (!isNaN(parsedDate.getTime())) {
                                instance.setDate(parsedDate, false);
                                valueInput.value = inputValue;
                            }
                        } catch (e) {
                            // Keep input as is if parsing fails
                        }
                    }
                },
                onReady: function(selectedDates, dateStr, instance) {
                    const flatpickrValue = valueInput.dataset.flatpickrValue || valueInput.value;
                    if (flatpickrValue) {
                        try {
                            const cleanFlatpickrValue = flatpickrValue.includes("+") ? flatpickrValue.split("+")[0] :
                                flatpickrValue.includes("-") ? flatpickrValue.split("-")[0] :
                                    flatpickrValue.includes("Z") ? flatpickrValue.replace("Z", "") : flatpickrValue;
                            let parsedDate;
                            if ((fieldType === "LocalTime" || fieldType === "OffsetTime") && /^\d{2}:\d{2}:\d{2}$/.test(cleanFlatpickrValue)) {
                                const [hours, minutes, seconds] = cleanFlatpickrValue.split(":");
                                parsedDate = new Date();
                                parsedDate.setHours(parseInt(hours, 10), parseInt(minutes, 10), parseInt(seconds, 10), 0);
                            } else if ((fieldType === "LocalDateTime" || fieldType === "OffsetDateTime" || fieldType === "Instant" || fieldType === "ZonedDateTime") && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(cleanFlatpickrValue)) {
                                const parts = cleanFlatpickrValue.split(" ");
                                const [year, month, day] = parts[0].split("-").map(Number);
                                const [hours, minutes, seconds] = parts[1].split(":").map(Number);
                                parsedDate = new Date(year, month - 1, day, hours, minutes, seconds);
                            } else {
                                parsedDate = new Date(cleanFlatpickrValue);
                            }

                            if (!isNaN(parsedDate.getTime())) {
                                instance.setDate(parsedDate, false);
                                valueInput.value = flatpickrValue;
                                if (timezoneInput && (fieldType === "OffsetTime" || fieldType === "OffsetDateTime" || fieldType === "Instant" || fieldType === "ZonedDateTime")) {
                                    if (flatpickrValue.includes("+")) {
                                        timezoneInput.value = "+" + flatpickrValue.split("+")[1];
                                    } else if (flatpickrValue.includes("-")) {
                                        timezoneInput.value = "-" + flatpickrValue.split("-")[1];
                                    } else if (flatpickrValue.includes("Z")) {
                                        timezoneInput.value = "Z";
                                    }
                                }
                            } else {
                                valueInput.value = flatpickrValue || "";
                            }
                        } catch (e) {
                            valueInput.value = flatpickrValue || "";
                        }
                    }
                }
            };

            valueInput.flatpickrInstance = flatpickr(valueInput, config);

            calendarIcon.addEventListener("click", function() {
                if (valueInput.flatpickrInstance) {
                    valueInput.flatpickrInstance.toggle();
                }
            });
        } else {
            valueContainer.style.display = "inline-block";
            calendarIcon.style.display = "none";
            if (timezoneInput) {
                timezoneInput.style.display = "none";
                timezoneInput.value = "";
            }
        }
        if (allowNullLabel) {
            allowNullLabel.style.display = operation === "is null" || operation === "is not null" ? "none" : "inline-block";
        }
    }

    const emptyStringLabel = operationSelect.parentElement.querySelector("label.empty-string");
    if (emptyStringLabel) {
        emptyStringLabel.style.display = fieldType === "String" ? "inline-block" : "none";
    }
}

// Add a new filter row
function addFilter() {
    const filterContainer = document.getElementById("filterContainer");
    if (!filterContainer) {
        return;
    }

    const fieldTypesElement = document.getElementById("fieldTypesData");
    const fieldsDataElement = document.getElementById("fieldsData");
    if (!fieldTypesElement || !fieldsDataElement) {
        return;
    }

    const fieldTypes = JSON.parse(fieldTypesElement.dataset.fieldTypes || "{}");
    const filterFields = fieldsDataElement.dataset.fields ? fieldsDataElement.dataset.fields.split(",") : [];

    const filterRow = document.createElement("div");
    filterRow.className = "filter-row";

    const fieldSelect = document.createElement("select");
    fieldSelect.name = "filterField";
    fieldSelect.addEventListener("change", function() { updateFilterOptions(this); });

    filterFields.forEach(field => {
        if (fieldTypes[field] !== "Collection" && !field.endsWith("_id")) {
            const option = document.createElement("option");
            option.value = field;
            option.textContent = field;
            fieldSelect.appendChild(option);
        }
    });

    const operationSelect = document.createElement("select");
    operationSelect.name = "filterOperation";
    operationSelect.addEventListener("change", function() { updateFilterControls(fieldSelect, this); });

    const valueContainer = document.createElement("div");
    valueContainer.className = "value-container";
    const valueInput = document.createElement("input");
    valueInput.type = "text";
    valueInput.name = "filterValue";
    valueInput.placeholder = "Value";
    valueInput.className = "filter-value-text";
    const calendarIcon = document.createElement("span");
    calendarIcon.className = "calendar-icon";
    calendarIcon.style.display = "none";
    calendarIcon.innerHTML = '<i class="fas fa-calendar-alt"></i>';
    const timezoneInput = document.createElement("input");
    timezoneInput.type = "text";
    timezoneInput.name = "timezoneValue";
    timezoneInput.placeholder = "Timezone (e.g. +03:00, Z)";
    timezoneInput.className = "timezone-input";
    timezoneInput.style.display = "none";
    valueContainer.appendChild(valueInput);
    valueContainer.appendChild(calendarIcon);
    valueContainer.appendChild(timezoneInput);

    const allowNullLabel = document.createElement("label");
    allowNullLabel.className = "allow-null";
    const allowNullCheckbox = document.createElement("input");
    allowNullCheckbox.type = "checkbox";
    allowNullCheckbox.name = "allowNull";
    allowNullCheckbox.value = "true";
    allowNullCheckbox.checked = false;
    allowNullLabel.appendChild(allowNullCheckbox);
    allowNullLabel.appendChild(document.createTextNode(" Allow NULL"));

    const emptyStringLabel = document.createElement("label");
    emptyStringLabel.className = "empty-string";
    const emptyStringCheckbox = document.createElement("input");
    emptyStringCheckbox.type = "checkbox";
    emptyStringCheckbox.name = "treatEmptyAsEmptyString";
    emptyStringCheckbox.value = "true";
    emptyStringCheckbox.checked = false;
    emptyStringCheckbox.addEventListener("change", function() { updateFilterControls(fieldSelect, operationSelect); });
    emptyStringLabel.appendChild(emptyStringCheckbox);
    emptyStringLabel.appendChild(document.createTextNode(" Empty String"));

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.textContent = "Remove";
    removeButton.addEventListener("click", function() { this.parentElement.remove(); });

    filterRow.appendChild(fieldSelect);
    filterRow.appendChild(operationSelect);
    filterRow.appendChild(valueContainer);
    filterRow.appendChild(allowNullLabel);
    filterRow.appendChild(emptyStringLabel);
    filterRow.appendChild(removeButton);

    filterContainer.appendChild(filterRow);
    updateFilterOptions(fieldSelect);
}

document.addEventListener("DOMContentLoaded", function() {
    const filterContainer = document.getElementById("filterContainer");
    if (!filterContainer) return;

    const filterRows = filterContainer.getElementsByClassName("filter-row");
    for (let row of filterRows) {
        const fieldSelect = row.querySelector("select[name='filterField']");
        const operationSelect = row.querySelector("select[name='filterOperation']");
        const allowNullCheckbox = row.querySelector("input[name='allowNull']");
        const emptyStringCheckbox = row.querySelector("input[name='treatEmptyAsEmptyString']");

        if (allowNullCheckbox) {
            allowNullCheckbox.setAttribute("data-checked", allowNullCheckbox.checked ? "true" : "false");
        }

        if (emptyStringCheckbox) {
            const wasEmptyStringChecked = emptyStringCheckbox.getAttribute("data-treatEmptyAsEmptyString") === "true" ||
                emptyStringCheckbox.getAttribute("data-checked") === "true";
            emptyStringCheckbox.checked = wasEmptyStringChecked || emptyStringCheckbox.checked;
            emptyStringCheckbox.addEventListener("change", function() {
                updateFilterControls(fieldSelect, operationSelect);
            });
        }

        updateFilterOptions(fieldSelect);
        updateFilterControls(fieldSelect, operationSelect);
    }

    window.applyFilters = function(page = 0) {
        const form = document.getElementById("filterForm");
        const rows = form.getElementsByClassName("filter-row");

        for (let row of rows) {
            const operationSelect = row.querySelector("select[name='filterOperation']");
            const valueInput = row.querySelector("input[name='filterValue']");
            const timezoneInput = row.querySelector("input[name='timezoneValue']");
            const allowNullCheckbox = row.querySelector("input[name='allowNull']");
            const emptyStringCheckbox = row.querySelector("input[name='treatEmptyAsEmptyString']");
            const fieldSelect = row.querySelector("select[name='filterField']");
            const fieldTypesElement = document.getElementById("fieldTypesData");
            const fieldType = fieldTypesElement && fieldTypesElement.dataset.fieldTypes ?
                JSON.parse(fieldTypesElement.dataset.fieldTypes)[fieldSelect.value] : "String";

            operationSelect.setAttribute("data-operation", operationSelect.value);
            if (allowNullCheckbox) {
                allowNullCheckbox.setAttribute("data-checked", allowNullCheckbox.checked ? "true" : "false");
            }
            emptyStringCheckbox.setAttribute("data-treatEmptyAsEmptyString", emptyStringCheckbox.checked ? "true" : "false");
            emptyStringCheckbox.setAttribute("data-checked", emptyStringCheckbox.checked ? "true" : "false");

            if (valueInput.value && (fieldType === "LocalTime" || fieldType === "LocalDateTime")) {
                valueInput.value = valueInput.value.includes("+") ? valueInput.value.split("+")[0] :
                    valueInput.value.includes("-") ? valueInput.value.split("-")[0] :
                        valueInput.value.includes("Z") ? valueInput.value.replace("Z", "") : valueInput.value;
            }

            if (timezoneInput && timezoneInput.value && valueInput.value &&
                (fieldType === "OffsetTime" || fieldType === "OffsetDateTime" || fieldType === "Instant" || fieldType === "ZonedDateTime")) {
                valueInput.value += timezoneInput.value;
            }

            let oldAllowNullInput = row.querySelector("input[type='hidden'][name='allowNull']");
            if (oldAllowNullInput) {
                oldAllowNullInput.remove();
            }

            const allowNullInput = document.createElement("input");
            allowNullInput.type = "hidden";
            allowNullInput.name = "allowNull";
            allowNullInput.value = allowNullCheckbox && (operationSelect.value !== "is null" && operationSelect.value !== "is not null") ?
                (allowNullCheckbox.checked ? "true" : "false") : "false";
            row.appendChild(allowNullInput);
        }

        document.getElementById("pageInput").value = page;
        form.submit();
    };
});