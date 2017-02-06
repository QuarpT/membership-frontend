import React from 'react'
import ReactDOM from 'react-dom';
import {DirectDebit} from 'modules/react/directDebit'
import {PromoCode, status} from 'modules/react/promoCode'
import {validatePromoCode, validatePromotionForPlans} from '../promoCode';
import {
    SortCode,
    validAccount,
    validEmail,
    validState,
    send,
    STRIPE,
    DIRECT_DEBIT,
    init as stripeInit
} from 'modules/renew/renew'


import SwitchButton from 'babel?presets[]=react&presets[]=es2015!react-switch-button'; //This ensures that this module gets correctly transpiled.
const empty = {
    value: '',
    isValid: false
};
export function init(container) {
    stripeInit();
    let showPaymentType = container.dataset.billingCountry === 'GB' && container.dataset.currency === 'GBP';
    let plans = window.guardian.plans;
    ReactDOM.render(<WeeklyRenew
        showPaymentType={showPaymentType}
        email={container.dataset.email}
        currency={container.dataset.currency}
        country={container.dataset.deliveryCountry}
        plans={plans}
        promoCode={container.dataset.promoCode}
        directDebitLogo={container.dataset.directDebitLogo}
        />,
        container);
}

class WeeklyRenew extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            error: false,
            email: props.email ? {value: props.email, isValid: true} : empty,
            paymentType: this.props.showPaymentType ? DIRECT_DEBIT : STRIPE,
            sortCode: empty,
            accountNumber: empty,
            accountHolder: empty,
            directDebitConfirmed: empty,
            loading: false,
            showValidity: false,
            plan: this.props.plans[0].id,
            plans: this.props.plans,
            promoCode: this.props.promoCode,
            promoStatus: status.NOTCHECKED,
            promotionDescription: null
        };
        this.showEmail = !this.state.email.isValid;
        this.handlePromo = this.handlePromo.bind(this);
        this.handleError = this.handleError.bind(this);
        this.handleEmail = this.handleEmail.bind(this);
        this.handlePaymentType = this.handlePaymentType.bind(this);
        this.handleSortCode = this.handleSortCode.bind(this);
        this.handleAccountNumber = this.handleAccountNumber.bind(this);
        this.handleAccountHolder = this.handleAccountHolder.bind(this);
        this.handleDirectDebitConfirmation = this.handleDirectDebitConfirmation.bind(this);
        this.click = this.click.bind(this);
        this.buttonText = this.buttonText.bind(this);
        this.validatePromo = this.validatePromo.bind(this);
        this.handlePlan = this.handlePlan.bind(this);
        this.getPlan = this.getPlan.bind(this);

        if(this.props.promoCode !== ''){
            this.validatePromo();
        }
    }


    click() {
        this.setState({showValidity: true});
        if (validState(this.state)) {
            this.setState({loading: true});
            send(this.state, this.handleError);
        }
    }

    handlePromo(e) {
        this.setState({
            promoCode: e.target.value.trim(),
            promoStatus: status.NOTCHECKED,
            plans: this.props.plans
        });

    };

    validatePromo() {
        this.setState({
            promosStatus: status.LOADING
        });

        validatePromoCode(this.state.promoCode, this.props.country, this.props.currency).then((a) => {
            let newPlans = validatePromotionForPlans(a, this.state.plans);
            let update = newPlans.map((plan) => {
                return 'promotionalPrice' in plan
            }).reduce((a, b) => {
                return !!(a || b)
            }, false);
            if (update) {
                //This is a weekly promotion
                this.setState({
                    promoStatus: status.VALID,
                    plans: newPlans,
                    promotionDescription: a.promotion.description
                })
            } else {
                //It's a good promotion, but it's not a weekly one
                this.setState({
                    promotionDescription: 'The promotion you have entered is not currently valid for any of the available billing periods.',
                    promoStatus: status.INVALID,
                    plans: this.props.plans
                })
            }
        }).catch((payload) => {
            let response = JSON.parse(payload.response);
            this.setState({
                promotionDescription: response.errorMessage,
                promoStatus: status.INVALID,
                plans: this.props.plans
            })
        });
    }

    handleEmail(e) {
        let email = e.target.value;
        this.setState({email: {value: email, isValid: validEmail(email)}});
    };

    handlePaymentType(e) {
        this.setState({paymentType: e.target.checked ? DIRECT_DEBIT : STRIPE})
    };

    handleSortCode(e) {
        this.setState({sortCode: new SortCode(e.target.value)})
    }

    handleAccountNumber(e) {
        let accountNumber = e.target.value;
        this.setState({accountNumber: {value: accountNumber, isValid: validAccount(accountNumber)}});
    }

    handleAccountHolder(e) {
        let name = e.target.value;
        this.setState({accountHolder: {value: name, isValid: name.length > 0}});
    }

    handleDirectDebitConfirmation(e) {
        let checked = e.target.checked;
        this.setState({directDebitConfirmed: {value: checked, isValid: checked === true}})
    }

    handlePlan(plan) {
        return () => {
            this.setState({plan: plan.id});
        }
    }

    getPlan() {
        return this.state.plans.filter((plan) => {
            return plan.id == this.state.plan
        })[0]
    }

    handleError(error) {
        this.setState({error: error, loading: false});
    }

    buttonText() {
        let plan = this.getPlan();
        let price = plan.promotionalPrice || plan.price;
        let method = this.state.paymentType === DIRECT_DEBIT ? 'by Direct Debit' : 'by Card';
        return ['Pay', price, method].join(' ');
    }

    render() {
        return <div>
            {this.state.loading && <div className="loader is-loading"> Processing</div> }
            {this.state.error && <div className="mma-error"> {this.state.error}</div>}
            {!this.state.loading && !this.state.error &&
            <div>

                <dl className="mma-section__list">

                    <PromoCode
                        value={this.state.promoCode}
                        handler={this.handlePromo}
                        send={this.validatePromo}
                        status={this.state.promoStatus}
                        copy={this.state.promotionDescription}
                    />
                    <PlanChooser plans={this.state.plans} selected={this.getPlan()} handleChange={this.handlePlan}/>
                    {this.showEmail &&
                    <EmailField value={this.state.email.value}
                                valid={!this.state.showValidity || this.state.email.isValid}
                                handleChange={this.handleEmail}/>}
                    <Payment
                        showPaymentType={this.props.showPaymentType}
                        handlePaymentType={this.handlePaymentType}
                        paymentType={this.state.paymentType}
                        handleSortCode={this.handleSortCode}
                        handleAccountNumber={this.handleAccountNumber}
                        handleAccountHolder={this.handleAccountHolder}
                        handleDirectDebitConfirmation={this.handleDirectDebitConfirmation}
                        sortCode={this.state.sortCode.value}
                        accountNumber={this.state.accountNumber.value}
                        accountHolder={this.state.accountHolder.value}
                        directDebitConfirmed={this.state.directDebitConfirmed.value}
                        validSortCode={!this.state.showValidity || this.state.sortCode.isValid}
                        validAccountNumber={!this.state.showValidity || this.state.accountNumber.isValid}
                        validAccountHolder={!this.state.showValidity || this.state.accountHolder.isValid}
                        validDirectDebitConfirmed={!this.state.showValidity || this.state.directDebitConfirmed.isValid}
                        directDebitLogo={this.props.directDebitLogo}
                    />
                </dl>
                <button
                    className="button button--primary"
                    onClick={this.click}>
                    {this.buttonText()}
                </button>
            </div>
            }

        </div>
    }
}

class EmailField extends React.Component {
    constructor(props) {
        super(props)

    }

    render() {
        let email = this.props.value;
        let invalid = this.props.valid === false;

        return <div>
            <dt className="mma-section__list--title">
                <label className="label" for="renew-email">Email address</label>
            </dt>
            <dd className="mma-section__list--content">
                <input id="renew-email" value={email} onChange={this.props.handleChange} className="input-text"/>
                {invalid && <p className="mma-error"> Please enter a valid email address</p>}
            </dd>
        </div>
    }
}

class Payment extends React.Component {
    constructor(props) {
        super(props);
    }

    render() {
        let showPaymentType = this.props.showPaymentType;
        return <div>{showPaymentType &&
        <PaymentType handlePaymentType={this.props.handlePaymentType} paymentType={this.props.paymentType}/>}
            {this.props.paymentType === DIRECT_DEBIT &&
            <DirectDebit
                handleSortCode={this.props.handleSortCode}
                handleAccountNumber={this.props.handleAccountNumber}
                handleAccountHolder={this.props.handleAccountHolder}
                handleDirectDebitConfirmation={this.props.handleDirectDebitConfirmation}
                sortCode={this.props.sortCode}
                accountNumber={this.props.accountNumber}
                accountHolder={this.props.accountHolder}
                directDebitConfirmed={this.props.directDebitConfirmed}
                validSortCode={this.props.validSortCode}
                validAccountNumber={this.props.validAccountNumber}
                validAccountHolder={this.props.validAccountHolder}
                validDirectDebitConfirmed={this.props.validDirectDebitConfirmed}
                directDebitLogo={this.props.directDebitLogo}
            />}
        </div>
    }
}

class PaymentType extends React.Component {
    constructor(props) {
        super(props);
    }

    render() {
        return <div>
            <dt className="mma-section__list--title">Payment method</dt>
            <dd className="mma-section__list--content">
                <SwitchButton labelRight="Direct Debit" label="Credit Card"
                              onChange={this.props.handlePaymentType}
                              checked={this.props.paymentType === DIRECT_DEBIT}
                              defaultChecked={this.props.paymentType === DIRECT_DEBIT}
                />
            </dd>
        </div>
    }
}

class PlanChooser extends React.Component {
    render() {
        let plans = this.props.plans.map((plan) => {
            let checked = plan == this.props.selected;
            return <Plan id={plan.id} price={plan.price} promotionalPrice={plan.promotionalPrice} checked={checked}
                         handleChange={this.props.handleChange(plan)}/>
        });
        return <div>
            <dt className="mma-section__list--title">Payment options</dt>
            <dd className="mma-section__list--content">
                {plans}
            </dd>
        </div>
    }
}
class Plan extends React.Component {
    price() {
        if (this.props.promotionalPrice) {
            return <span>
                <s>{this.props.price} </s>
                <strong>{this.props.promotionalPrice}</strong>
            </span>
        }
        return this.props.price
    }

    render() {
        return <label className="option"><input type="radio" name="planchooser" value={this.props.id}
                                                checked={this.props.checked}
                                                onChange={this.props.handleChange}/>
            {this.price()}
        </label>
    }
}