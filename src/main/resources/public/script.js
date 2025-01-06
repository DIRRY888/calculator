// 配置Amazon Cognito相关信息
const cognitoConfig = {
    region: 'ap-northeast-2',
    userPoolId: 'ap-northeast-2_QQUVB9CQV',
    userPoolWebClientId: 'tj8gpi4793fpd97qh4epv8h7k'
};

// 初始化Amazon Cognito服务
Amplify.configure({
    Auth: cognitoConfig
});

const loginButton = document.getElementById('login-button');
const calculateButton = document.getElementById('calculate-button');
const loginSection = document.getElementById('login-section');
const priceCalculatorSection = document.getElementById('price - calculator - section');
const resultDiv = document.getElementById('result');

// 登录功能
loginButton.addEventListener('click', async () => {
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    try {
        await Auth.signIn(username, password);
// 登录成功，隐藏登录区域，显示价格计算器区域
        loginSection.style.display = 'none';
        priceCalculatorSection.style.display = 'block';
    } catch (error) {
        console.error('Login error:', error);
    }
});

// 价格计算功能
calculateButton.addEventListener('click', async () => {
    const accessToken = await Auth.currentSession().then((data) => data.getAccessToken().getJwtToken());
    const quantity = document.getElementById('quantity').value;
    const productId = document.getElementById('productId').value;
    try {
        const response = await fetch('YOUR_API_GATEWAY_URL/calculatePrice', {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer'+ accessToken
            },
            body: JSON.stringify({
                quantity: quantity,
                productId: productId
            })
        });
        const data = await response.json();
        resultDiv.textContent = `Total Price: $${data.price}`;
    } catch (error) {
        console.error('Price calculation error:', error);
    }
});