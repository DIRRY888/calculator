function deleteRow(button) {
    const row = button.parentElement;
    row.remove();
    const valueRows = document.querySelectorAll('.value-row');
    if (valueRows.length === 0) {
        // 如果删除后没有剩余的申报价值行了，就将序号重置为1
        currentValueIndex = 1;
    } else {
        if (currentValueIndex > 1) {
            currentValueIndex--;
        }
    }
}
let currentValueIndex = 1; // 初始化为1，表示第一个申报货物价值

function addValue() {
    const valueRowsDiv = document.getElementById('valueRows');
    const newRow = document.createElement('div');
    newRow.className = 'value-row';
    newRow.innerHTML = `
            <div class="value-item">
                <label for="value${currentValueIndex + 1}">总申报货物${currentValueIndex + 1}价值（单位：USD）:</label>
                <input type="number" id="value${currentValueIndex + 1}">
            </div>
            <button class="delete-row delete-button" onclick="deleteRow(this)">X</button>
            <div class="value-item">
                <label for="dutyRate${currentValueIndex + 1}">进口清关税率（%）:</label>
                <input type="number" id="dutyRate${currentValueIndex + 1}" step="0.01">
            </div>
        `;
    valueRowsDiv.appendChild(newRow);
    currentValueIndex++;
}
function submitForm() {
    console.log('submitForm function called');
    const containerLoadingType = document.getElementById('containerLoadingType').value;
    console.log('containerLoadingType:', containerLoadingType);
    const POL = document.getElementById('POL').value;
    console.log('POL:', POL);
    const cargoType = document.getElementById('cargoType').value;
    console.log('cargoType:', cargoType);
    const volume = document.getElementById('volume').value;
    if (volume === "") {
        alert('请输入订舱总体积，不能为空');
        return;
    }
    console.log('volume:', volume);
    const weight = document.getElementById('weight').value;
    if (weight === "") {
        alert('请输入订舱总重量，不能为空');
        return;
    }
    console.log('weight:', weight);
    const carton = document.getElementById('carton').value;
    if (carton === "") {
        alert('请输入Carton的值，不能为空');
        return;
    }
    console.log('carton:', carton);
    const startDate = document.getElementById('startDate').value;
    if (startDate === "") {
        alert('请输入存储开始时间，不能为空');
        return;
    }
    console.log('startDate:', startDate);
    const endDate = document.getElementById('endDate').value;
    if (endDate === "") {
        alert('请输入存储结束时间，不能为空');
        return;
    }
    console.log('endDate:', endDate);
    const storageDays = document.getElementById('storageDays').value;
    if (storageDays === "") {
        alert('请输入AWD库内存储天数，不能为空');
        return;
    }
    console.log('storageDays:', storageDays);
    const inputMethod = document.getElementById('inputMethod').value;
    console.log('inputMethod:', inputMethod);
    const tableData = [];
    let ibFee = 0;

    // 获取所有申报货物价值和进口清关税率
    const values = [];
    const dutyRates = [];
    const valueRows = document.querySelectorAll('.value-row input');
    for (let i = 0; i < valueRows.length; i += 2) {
        const value = valueRows[i].value;
        const dutyRate = valueRows[i + 1].value;
        if (value === "") {
            alert(`请输入总申报货物${i / 2 + 1}价值，不能为空`);
            return;
        }
        if (dutyRate === "") {
            alert(`请输入总申报货物${i / 2 + 1}对应的进口清关税率，不能为空`);
            return;
        }
        values.push(parseFloat(value));
        dutyRates.push(parseFloat(dutyRate));
    }
    console.log('values:', values);
    console.log('dutyRates:', dutyRates);
    // 新增验证代码
    const value1 = document.getElementById('value1').value;
    if (value1 === "") {
        alert('请输入总申报货物1价值，不能为空');
        return;
    }

    const dutyRate1 = document.getElementById('dutyRate1').value;
    if (dutyRate1 === "") {
        alert('请输入进口清关税率，不能为空');
        return;
    }
    if (inputMethod === 'table'){
        const tableRows = document.querySelectorAll('table tr');
        tableRows.forEach(row => {
            const cells = row.querySelectorAll('td input.table-input');
            cells.forEach(cell => {
                tableData.push(cell.value);
            });
        });
        console.log('tableData:', tableData);
    }else if (inputMethod === 'direct') {
        ibFee = document.getElementById('ibFee').value;
        if (ibFee === "") {
            alert('请输入IB FEE的值，不能为空');
            return;
        }
        console.log('ibFee:', ibFee);
    }
    // 构建请求数据
    const data = {
        containerLoadingType: containerLoadingType,
        POL: POL,
        cargoType: cargoType,
        volume: volume,
        weight: weight,
        carton: carton,
        values: values,
        dutyRates: dutyRates,
        startDate: startDate,
        endDate: endDate,
        storageDays: storageDays,
        inputMethod: inputMethod,
        tableData: tableData,
        ibFee: ibFee
    };
    const serializedData = JSON.stringify(data);
    console.log('手动序列化后的结果:', serializedData);
    // 发送POST请求到后端
    fetch('/dev/calculate', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
    })
        .then(response => response.json())
        .then(result => {
            let tableHtml = result.tableHtml;
            const modalTableContainer = document.getElementById('modalTableContainer');
            modalTableContainer.innerHTML = tableHtml;
            document.getElementById('myModal').style.display = 'block';
            closeModal();
        })
        .catch(error => {
            console.error('Error:', error);
        });
}

function closeModal() {
    const modal = document.getElementById('myModal');
    const closeBtn = document.getElementsByClassName('close')[0];
    closeBtn.addEventListener('click', function () {
        modal.style.display = 'none';
    });
}

function handleInputMethodChange() {
    console.log('handleInputMethodChange function called');
    console.log('this object:', this);
    let selectedMethod = document.getElementById('inputMethod');
    selectedMethod = selectedMethod.options[selectedMethod.selectedIndex].value
    console.log('Selected input method:', selectedMethod);
    const table = document.getElementById('tableData');
    console.log('table:', table);
    const directInput = document.getElementById('directInput');
    console.log('directInput:', directInput);
    console.log('Before change: Table display =', table.style.display, ', DirectInput display =', directInput.style.display);
    if (selectedMethod === 'table') {
        console.log('-------------------------table');
        table.style.display = 'table';
        directInput.style.display = 'none';
    } else {
        console.log('input');
        table.style.display = 'none';
        directInput.style.display = 'block';
    }
    console.log('After change: Table display =', table.style.display, ', DirectInput display =', directInput.style.display);
}

function initCalculator() {
    const addValueRowButton = document.getElementById('addValueRow');
    console.log('addValueRowButton:', addValueRowButton);
    const inputMethodSelect = document.getElementById('inputMethod');
    console.log('inputMethodSelect:', inputMethodSelect);
    const submitButton = document.getElementById('submitButton');
    console.log('submitButton:', submitButton);

    if (addValueRowButton) {
        addValueRowButton.addEventListener('click', addValue);
    } else {
        console.error('addValueRowButton not found.');
    }

    const deleteButtons = document.querySelectorAll('.delete-row');
    deleteButtons.forEach(button => {
        button.addEventListener('click', () => deleteRow(button));
    });

    if (inputMethodSelect) {
        inputMethodSelect.addEventListener('change', handleInputMethodChange.bind(inputMethodSelect));
        console.log('Binding event to inputMethodSelect');
    } else {
        console.error('inputMethodSelect not found.');
    }

    if (submitButton) {
        submitButton.addEventListener('click', submitForm);
    } else {
        console.error('submitButton not found.');
    }
}